# Design: Private Beta Launch Readiness

## Overview

Implement the change as evidence-bounded slices over existing waitlist, platform-admin, identity, tenancy, publishing, and deployment primitives. Keep code evidence (unit/BDD/WireMock/E2E) separate from managed-VPS observations. The final DALLAY-559 record is a gate over classified prerequisites, not a new runtime subsystem.

## Changes

### Architecture Decisions

| Decision | Choice | Alternatives / rationale |
|---|---|---|
| Delivery order | A: DALLAY-520, then DALLAY-556; B: DALLAY-555 and DALLAY-557 in parallel; C: DALLAY-558 after A plus minimum B; DALLAY-559 last. | A single feature branch would hide operational blockers and increase review size. These slices give each prerequisite a clear finish and rollback. |
| Invitation ownership | Model `Invitation` as a first-class capability independent from waitlist. It carries `source` (`DIRECT` or `WAITLIST`), optional `sourceReferenceId`, mandatory `workspaceId`, normalized target email, hashed token, semantic lifecycle, issuer, timestamps, and accepted principal metadata. Expose acceptance through a Mediator command and explicit application ports to identity and tenancy. | Do not make `waitlistEntryId` mandatory, couple invitation validity to delivery state, put invitation rules in controllers/infrastructure, or let the frontend mutate membership or choose `workspaceId`. |
| Tenant safety | Reconcile membership through a tenancy application API keyed by `(workspaceId, principalId)` with a database uniqueness guarantee; every subsequent request still uses `X-Workspace-Id` and existing authorization resolution. | Do not create a second membership model or bypass the existing application-level tenancy boundary. |
| Worker control | Use the existing `publishing.worker.enabled` gate as the safe-off switch, rendered from managed deployment configuration and verified before provider calls. | No public UI toggle: operator control must be auditable, reversible, and unavailable to ordinary workspace users. |
| Evidence boundary | Store redacted records with UTC timestamp, environment, release/namespace, scope, operator, outcome, and classification. | Local/CI results cannot prove VPS behavior; VPS observation cannot be called provider verification. |

### Data Flow

```text
Waitlist activation (520)
  -> admin invite/delivery
  -> hashed-token acceptance (556)
  -> identity register/login/email verification
  -> atomic invitation consume + tenancy membership reconcile
  -> workspace list / X-Workspace-Id
  -> scheduler/composer (558)
  -> publication queued -> worker gate -> provider attempt
  -> lifecycle state, safe UI result, operator evidence (555/557)
```

Acceptance is one transaction: validate active/unexpired invitation, resolve or create the identity through existing identity contracts after verifying normalized email, reconcile exactly one membership for the invitation's mandatory `workspaceId`, mark the invitation accepted with the principal identity, and only then update optional waitlist conversion state when `source == WAITLIST`. A consumed, revoked, expired, altered, or concurrently lost token returns a deterministic invalid-or-consumed error contract and performs no membership mutation. Repeated login or delivery retries use existing identity uniqueness and membership upsert semantics; raw tokens never persist or appear in logs. Invitation acceptance MUST NOT mutate email-verification state, and first login is a flow state rather than an Invitation aggregate state.

### File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/{application,infrastructure}` | Modify/Create | Mediator acceptance command/handler, identity/tenancy ports, public acceptance adapter, safe DTOs; preserve admin-only invite controls. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/{application,infrastructure}` | Modify | Idempotent membership lookup/upsert and uniqueness-safe persistence. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling` | Modify | Expose safe-off/readiness diagnostics while retaining claim, retry, lifecycle logging, and redaction behavior. |
| `apps/web/app/src/modules/auth/{presentation,infrastructure}`, `apps/web/app/src/router/index.ts` | Modify/Create | Invite acceptance, first-login redirect, workspace hydration, and safe expired/replayed states. |
| `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` | Create | Invitee journey through workspace, scheduler, unavailable capability, and typed failure. |
| `server/smp/src/test/{kotlin,resources/features}` and `apps/web/app/src/modules/**` tests | Modify/Create | Mandatory backend BDD plus focused unit/integration and frontend tests. |
| `infra/apps/smp/swarm/stack.yaml`, `docs/infrastructure/private-beta-launch-readiness-runbook.md` | Modify/Create | Configurable worker safe-off, managed route/readiness, backup/restore, rollback, failure/stale-job and operator procedures. |

### Interfaces / Contracts

```kotlin
enum class InvitationSource { DIRECT, WAITLIST }
enum class InvitationStatus { ACTIVE, ACCEPTED, EXPIRED, REVOKED }

data class AcceptInvitationCommand(
    val rawToken: String,
    val authenticatedPrincipalId: String?,
    val authenticatedEmail: String?,
)
data class InvitationAcceptanceResult(
    val workspaceId: String,
    val membershipStatus: String,
)
interface WorkspaceMembershipProvisioner {
    suspend fun reconcile(workspaceId: String, principalId: String): WorkspaceMembershipSnapshot
}
```

The acceptance endpoint returns only a safe result/error; invitation summaries expose status and timestamps, never token material. Publishing responses expose canonical lifecycle states and allowlisted failure categories only.

## Usage

### Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Token lifecycle, replay, membership reconciliation, safe-off, stale/failure mapping | JUnit/Kotlin existing domain and worker tests; Vitest for stores/views and failure copy. |
| Integration/BDD | Transactional acceptance, email-verification boundary, workspace isolation, publication lifecycle and disabled worker | Add tagged Cucumber scenarios (`@smoke`, `@fast`) and Postgres/Testcontainers coverage; use WireMock for provider outcomes. |
| E2E | Invitee first login, workspace context, scheduler/composer, unavailable provider, safe failure | Focused Playwright spec with fixtures and user-visible assertions; managed-VPS run is separately classified operator evidence. |

### Observability and Evidence

Reuse `PublishingLifecycleLogger` events and canonical failure taxonomy; add stale-job/operator summaries without exposing exceptions, provider bodies, credentials, storage paths, raw invitation tokens, or full invitee email addresses. Managed-VPS evidence must identify hostname, active namespace, release identity, UTC time, operator, scope, result, and limitation. Route checks cover public API plus private readiness/management access; PostgreSQL, direct origin listeners, and port 9091 must remain externally blocked.

All live publishing claims remain `USER_REPORTED_OPERATIONAL`. Evidence classification (`CODE_VERIFIED`, `TEST_VERIFIED`, `VPS_OBSERVED`, `OPERATOR_REPORTED`, or `UNVERIFIED`) is separate from publication claim status. This change produces no provider verification and must not infer it from a successful user report; it must not create `MULTI_USER_VERIFIED` claims.

## Troubleshooting

### Migration / Rollout

`server/smp/src/main/resources/db/changelog/platform-admin/004-create-invitations.yaml` creates the first-class `invitations` table. Liquibase applies it after the existing workspace and principal prerequisites, so invitation persistence is available before invitation acceptance is enabled. The current code introduces this table without a legacy invitation source, therefore no data backfill is required for the new table; any existing waitlist state remains in its existing tables and is converted only through the explicit invitation flow.

Keep the publishing worker safe-off during deployment and enable it only after the minimum DALLAY-557 managed-environment evidence is recorded. If rollout fails, stop before enablement, disable invitations and worker execution, roll back the deployment/change set using the last-known-good release and documented backup/restore rehearsal, and do not perform destructive operations against active beta data. Historical evidence remains retained.

### Open Questions

- [ ] Confirm the managed beta hostname, release identifier format, and evidence retention location before the operator run.
- [ ] Confirm the approved isolated restore target and test-data scope for the VPS rehearsal.

## References

- DALLAY-520, DALLAY-555, DALLAY-556, DALLAY-557, DALLAY-558, and DALLAY-559.
- Existing waitlist, invitation, IAM/tenancy, publishing, deployment, monitoring, and test infrastructure.
