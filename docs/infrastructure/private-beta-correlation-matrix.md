# Private Beta Correlation Matrix (DALLAY-557)

**Audience:** On-call operator and any reviewer looking at backend logs.
**Companion:** [`private-beta-operator-checklist.md`](./private-beta-operator-checklist.md), [`private-beta-launch-readiness-runbook.md`](./private-beta-launch-readiness-runbook.md).

This matrix enumerates every correlation identifier an operator can use to pivot between
the operator checklist, the backend log, the audit trail, and the issue tracker. It also
records the fields that MUST NOT appear in any log line, response body, audit event, or
operator-visible message — the redaction contract.

## Correlation identifiers

| Identifier | Format | Source | Where it appears | Lifetime |
|---|---|---|---|---|
| `jobId` | UUID | `publication_jobs.id` | Backend publish log lines, `PublishingStaleJobsQuery` response, audit events under `WAITLIST_*` / `PUBLICATION_*` actions, the OpenSpec `private-beta-launch-readiness/specs/publishing/spec.md` scenarios | From `PENDING` insertion until the job terminates in `PUBLISHED`, `FAILED`, or `BLOCKED` |
| `attemptNumber` | int (>= 1) | `publication_jobs.attempt_number` | Publish log lines, stale-job response, retry-policy decisions | Reset only on terminal status; increments on every retryable failure |
| `workspaceId` | UUID | `publication_jobs.workspace_id` | Publish log lines, audit events, admin endpoint responses | Permanent (workspace lifecycle) |
| `publicationId` | UUID | `publication_jobs.publication_id` → `publications.id` | Publish log lines, admin endpoint responses | Permanent (publication lifecycle) |
| `waitlistEntryId` | UUID | `waitlist_entries.id` | Admin waitlist responses, audit events under `WAITLIST_ENTRY_*`, invitation issuance responses | Permanent |
| `invitationId` | UUID | `waitlist_invitations.id` | Admin invite/cancel/revoke responses, audit events under `WAITLIST_INVITATION_*` | Permanent |
| `auditEventId` | UUID | `audit_events.id` | Backend audit hook (R2DBC), admin audit endpoints | Permanent (append-only ledger) |
| `claimToken` | opaque string `<worker>-<UUID>` | `publication_jobs.claimed_by_worker` | Stale-job response, lease-state SQL only; never logged | Resets to `NULL` when the row returns to `PENDING` |

The same identifiers appear in the OpenSpec `private-beta-launch-readiness/specs/publishing/spec.md`
publishing scenarios and the `platform-admin.feature` BDD scenarios; cross-check the
Gherkin name → backend log key mapping there when writing new log lines.

## Pivot recipes

### From a `jobId` (operator sees a slow post)

1. `grep "$jobId" backend.log` to follow the attempt timeline (`Polling → claim → publish → terminal`).
2. `SELECT id, status, attempt_number, claimed_by_worker, lease_expires_at, last_error_class FROM publication_jobs WHERE id = '$jobId';` (admin DB read-only role).
3. `SELECT action, principal_id, occurred_at, event_id FROM audit_events WHERE (payload->>'jobId')::uuid = '$jobId' ORDER BY occurred_at;` for the actor trail.
4. If the operator needs a stale-job call, use [/api/admin/publishing/stale-jobs?leaseStaleThreshold=PT5M](private-beta-launch-readiness-runbook.md#stale-visibility)
   with the `PUBLISHING_STALE_READ` permission.

### From an `invitationId` (operator receives a support ticket)

1. `SELECT id, status, token_hash, waitlist_entry_id, expires_at, revoked_at, last_used_at FROM waitlist_invitations WHERE id = '$invitationId';`
2. `SELECT action, principal_id, occurred_at, event_id FROM audit_events WHERE (payload->>'invitationId')::uuid = '$invitationId' ORDER BY occurred_at;`
3. Cross-link to the `waitlist_entry_id` to retrieve `email_hash` (the normalized lookup key)
   and continue with the waitlist pivot.

### From a `waitlistEntryId` (Cohort lead asks why they cannot log in)

1. `SELECT id, status, email_hash, created_at, last_email_sent_at FROM waitlist_entries WHERE id = '$waitlistEntryId';`
2. `SELECT … FROM waitlist_invitations WHERE waitlist_entry_id = '$waitlistEntryId' ORDER BY created_at;`
3. `SELECT … FROM audit_events WHERE (payload->>'waitlistEntryId')::uuid = '$waitlistEntryId';`
4. If the issue is auth-side, pull the linked `principalId` and query
   `SELECT … FROM audit_events WHERE principal_id = '$principalId' AND action LIKE 'AUTH_%' ORDER BY occurred_at DESC LIMIT 50;`

## Redaction contract

These fields MUST NEVER appear outside the secrets-bound configuration of the application:

| Field | Why | Where it can legitimately exist |
|---|---|---|
| `db_password` (or any value of the `db_password` Swarm secret) | Master DB credential | `/run/secrets/db_password` mounted read-only into the `postgresql` and `backend` tasks (mode `0400`) |
| `local_jwt_secret` (or any value of `local_jwt_secret`) | Backend local JWT signing key | `/run/secrets/local_jwt_secret` mounted read-only into `backend` (mode `0400`) |
| `publishing_credentials_key` | Symmetric encryption key for stored LinkedIn OAuth tokens at rest | `/run/secrets/publishing_credentials_key` (mode `0400`) |
| `media_preview_signing_secret` | HMAC for media previews | `/run/secrets/media_preview_signing_secret` (mode `0400`) |
| `linkedin_state_signing_secret` | OAuth state signing | `/run/secrets/linkedin_state_signing_secret` (mode `0400`) |
| `linkedin_client_secret` | LinkedIn OAuth client secret | `/run/secrets/linkedin_client_secret` (mode `0400`) |
| `resend_api_key` | Transactional email provider key | `/run/secrets/resend_api_key` (mode `0400`) |
| Raw invitation tokens | Bearer of acceptance | Only inside `waitlist_invitations.token_hash` (HMAC-SHA256, never the raw value) |
| Raw password-reset tokens | Bearer of reset | Only inside `password_reset_tokens.token_hash` (HMAC-SHA256, never the raw value) |
| Bearer access tokens | Session credential | Cookie or `Authorization` header only; never logged |
| Refresh tokens | Session credential | Cookie only; never logged |
| Provider OAuth access/refresh tokens | Session credential for LinkedIn | Encrypted at rest via `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY`; never logged |
| Provider API keys (Unsplash, Resend, anything new) | Provider credential | Encrypted or secret-mounted only; never logged |

Implementation guards that already exist:

- Spring Boot's `RequestContextFilter` redacts `Authorization`, `Cookie`, and `Set-Cookie`
  headers from access logs.
- `PublishingStaleJobsController` returns `claimed_by_worker` only — no `claimToken` field
  exists in the response DTO.
- `InvitationAcceptanceController` never echoes the raw `invitationToken`; the response body
  intentionally omits it and the BDD scenario `Auth.. response should not contain the token`
  enforces this.
- `IdentityController` reset/forgot-password endpoints store `token_hash` only and never
  log the raw token.

Implementation guards that are open work (not yet enforced by automated tests at the time
of this runbook):

- A blanket audit-event redaction review: every `audit_events.payload` row is reviewed at
  write time by `R2dbcAuditHook` (see `server/smp/src/main/kotlin/com/profiletailors/smp/audit/infrastructure/R2dbcAuditHook.kt`)
  for known sensitive keys. The redaction set is currently the explicit list in
  `AuditRedactionPolicy` plus the implicit denylist in `request-context`. New sensitive
  keys MUST be added to `AuditRedactionPolicy` rather than relying on operator diligence.
- A backend-log lint test that fails the build if a `*Token*` or `*Secret*` token name
  appears in a `println`/`logger.*` call inside the `server/smp` module. This is open
  follow-up work tracked separately.

## Reclassification note

This document is `USER_REPORTED_OPERATIONAL`. Any entry that depends on a provider
confirmation (LinkedIn rate-limit reply, Resend delivery webhook, etc.) MUST NOT be marked
provider-verified or multi-user-verified by an operator running this checklist.
