# Data Retention Operations Guide (Current State)

**Last Updated: 2026-09-29

> **Classification:** Internal — Operations and Compliance
> **Status:** Current-state operations guide — the retention governance API is planned, not implemented
> **Last Updated: 2026-09-29
>
> **IMPORTANT:** Earlier drafts of this guide described a retention rule/purge/hold HTTP API
> (`/api/governance/retention/*`) and a `V100__retention_governance.xml` migration. Neither
> exists. This corrected guide documents only the retention operations that are actually
> implemented. Planned framework capabilities are listed at the end with a clear **Planned**
> marker and must not be assumed available.

## Overview

Retention enforcement today is implemented as four scheduled jobs, one request-driven
credential deletion, plus one governance compliance control. There is no central rule engine.
The per-activity retention status register is maintained in [`docs/compliance/data-inventory.yaml`](compliance/data-inventory.yaml)
and the gap/plan document is
[`docs/compliance/retention-and-erasure-control-plan.md`](compliance/retention-and-erasure-control-plan.md).

## Current Retention Mechanisms

### 1. Media orphan garbage collection (7-day retention)

- **Job:** `BlobGarbageCollector` — runs hourly (initial delay applies), deletes orphaned
  storage objects for blobs past the 7-day retention period.
- **Supporting job:** `MediaAssetExpirationJob` — runs every 6 hours, transitions stale
  `PENDING_UPLOAD`/`UPLOADING` assets to `FAILED` and schedules orphaned blobs for GC.
- **Scheduler:** `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/MediaReconcilerScheduler.kt`
- **Scope:** media storage objects only. Database rows and other data classes are not covered.

### 2. Data subject request expiry (30-day retention)

- **Job:** `FindExpiredRequestsJob` — runs daily, discovers data subject requests past their
  retention expiry (30 days from creation) and deletes them.
- **Scheduler:** `server/smp/src/main/kotlin/com/profiletailors/smp/privacy/infrastructure/config/PrivacyScheduler.kt`

### 3. Password-reset token cleanup (configurable retention)

- **Job:** `PasswordResetTokenCleanupScheduler` — deletes expired password-reset tokens beyond
  the configured retention period.
- **Configuration:** `smp.identity.password-recovery.cleanup.retention` (default 24 h interval,
  5 m initial delay; retention must be non-negative).
- **Scheduler:** `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/PasswordResetTokenCleanupScheduler.kt`

### 4. OAuth credential deletion on disconnect (tombstoned, idempotent)

- **Flow:** `DELETE /api/publishing/{provider}/connections/{connectionId}` deletes the
  social accounts/content, the `secure_credentials` row, and the connection in one
  transaction, then records a `credential_deletion_tombstones` row
  (`workspace_id, connection_id, provider, reason=DISCONNECT`, traced to `pa-006`).
- **Handler:** `DisconnectProviderConnectionHandler` — a repeated disconnect returns the
  same `DELETED` result without side effects; a restored-then-redisconnected row is
  re-deleted because the live row is checked before the tombstone.
- **Scope:** OAuth credentials only. Expired-but-referenced credentials and provider-side
  propagation are not covered (slice 2).

### 5. Expired orphan-credential purge (disabled by default)

- **Job:** `CredentialRetentionJob` — scans `secure_credentials` per provider for rows whose
  `access_token_expires_at` is past the configured retention (default P30D, overridable per
  provider via `publishing.credentials.retention.providers.*`) and deletes those not
  referenced by an `ACTIVE`/`REQUIRES_RECONNECT` connection, in batches with
  `FOR UPDATE SKIP LOCKED`.
- **Scheduler:** `CredentialRetentionScheduler` — disabled unless
  `publishing.credentials.retention.enabled=true`; interval default PT6H.
- **Dry-run:** set `publishing.credentials.retention.dry-run=true` (or call
  `run(dryRun=true)`): reports counts plus non-sensitive sample IDs, writes nothing.
- **Scope:** orphan rows only. Credentials still referenced by terminal
  (`EXPIRED`/`REVOKED`/`DISABLED`) connections are left for slice 2.

### 6. Retention compliance control (governance register)

- The compliance control `ctrl-privacy-data-retention` (`PRIVACY.DATA_RETENTION`) is seeded in
  the `compliance_controls` table (Liquibase `003-seed-compliance-controls.yaml`) and is
  required for the MVP release scope and the EEA market.
- It can be evaluated via `POST /api/governance/compliance/evaluations` and surfaced through
  `GET /api/governance/compliance/release-gate`. This registers *whether* retention is
  controlled; it does not enforce retention periods.

## Scheduled Job Summary

| Job | Cadence | Scope | Retention period |
| --- | ------- | ----- | ---------------- |
| `BlobGarbageCollector` | Hourly | Media orphaned blobs | 7 days |
| `MediaAssetExpirationJob` | Every 6 h | Stale media assets | — (state transition) |
| `FindExpiredRequestsJob` | Daily | Data subject requests | 30 days |
| `PasswordResetTokenCleanupScheduler` | 24 h | Expired password-reset tokens | Configurable |
| `CredentialRetentionJob` | 6 h (disabled by default) | Orphaned expired OAuth credentials | P30D default, per-provider override |
| Disconnect flow | On request | OAuth credential + connection | Immediate + tombstone |

## Troubleshooting

- **A media blob older than 7 days is still present:** The GC only removes *orphaned* blobs
  (blobs whose database row is gone or eligible). Check whether the blob still has an active
  database reference; if so, it is not eligible. Failures are tracked by the job.
- **A DSR older than 30 days is still present:** Confirm the request type is subject to the
  expiry job and that the scheduler is enabled (`@Scheduled` requires `spring.task.scheduling`
  enabled in the profile).
- **A disconnected credential is still present:** Confirm the disconnect response was `DELETED`;
  orphaned expired credentials are only purged when `publishing.credentials.retention.enabled=true`.
  A credential referenced by an `ACTIVE` connection is never eligible.
- **A repeated disconnect behaves differently:** Replays return `DELETED` from the tombstone
  without side effects. If the row was restored from backup, the live row is re-deleted and the
  tombstone refreshed — that is the intended restore replay for this scope.
- **Retention behaviour differs from `data-inventory.yaml`:** The inventory is an evidence
  register. Treat any inventory row whose control is `Not implemented` or `Partial` as
  non-enforced.

## Planned (NOT implemented — do not depend on these)

| Capability | Target endpoint / artifact |
| ---------- | -------------------------- |
| Retention rule registration (planned) | `POST /api/governance/retention/rules` — not implemented |
| Rule approval workflow | `PUT /api/governance/retention/rules/{id}/approve` |
| Purge scheduling / status | `POST\|GET /api/governance/retention/purges` |
| Legal/operational holds | `POST\|GET /api/governance/retention/holds` |
| Deletion tombstones | `deletion_tombstones` table |
| Purge evidence | `GET /api/governance/retention/purges/{id}/evidence` |
| Migration | `V100__retention_governance.xml` (5 tables, 12 indexes) |

Until these are implemented, retention enforcement is limited to the six mechanisms above.

## Support

- **Product team:** #profile-tailors-smp
- **Compliance:** via `docs/compliance/README.md` (internal controls are not public policy)
