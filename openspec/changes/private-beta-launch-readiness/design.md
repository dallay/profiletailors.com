# Design: Private Beta Launch Readiness

## Technical Approach

Implement the change as evidence-bounded slices over existing waitlist, platform-admin, identity, tenancy, publishing, and deployment primitives. Keep code evidence (unit/BDD/WireMock/E2E) separate from managed-VPS observations. The final DALLAY-559 record is a gate over classified prerequisites, not a new runtime subsystem.

## Architecture Decisions

| Decision | Choice | Alternatives / rationale |
|---|---|---|
| Delivery order | A: DALLAY-520, then DALLAY-556; B: DALLAY-555 and DALLAY-557 in parallel; C: DALLAY-558 after A plus minimum B; DALLAY-559 last. | A single feature branch would hide operational blockers and increase review size. These slices give each prerequisite a clear finish and rollback. |
| Invitation ownership | Keep token validation in `platformadmin` (the existing `WaitlistInvitation` aggregate and hashed-token repository); expose acceptance through a Mediator command and explicit application ports to identity and tenancy. | Do not put invitation rules in controllers or infrastructure, and do not let the frontend mutate membership directly. |
| Tenant safety | Reconcile membership through a tenancy application API keyed by `(workspaceId, principalId)` with a database uniqueness guarantee; every subsequent request still uses `X-Workspace-Id` and existing authorization resolution. | Do not create a second membership model or bypass the existing application-level tenancy boundary. |
| Worker control | Use the existing `publishing.worker.enabled` gate as the safe-off switch, rendered from managed deployment configuration and verified before provider calls. | No public UI toggle: operator control must be auditable, reversible, and unavailable to ordinary workspace users. |
| Evidence boundary | Store redacted records with UTC timestamp, environment, release/namespace, scope, operator, outcome, and classification. | Local/CI results cannot prove VPS behavior; VPS observation cannot be called provider verification. |

## Data Flow

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

Acceptance is one transaction: validate active/unexpired invitation, resolve or create the invited identity through existing identity contracts, reconcile exactly one workspace membership, mark the invitation accepted, and convert the waitlist entry. A consumed, revoked, expired, altered, or concurrently lost token returns the safe error contract and performs no membership mutation. Repeated login or delivery retries use existing identity uniqueness and membership upsert semantics; raw tokens never persist or appear in logs.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/{application,infrastructure}` | Modify/Create | Mediator acceptance command/handler, identity/tenancy ports, public acceptance adapter, safe DTOs; preserve admin-only invite controls. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/{application,infrastructure}` | Modify | Idempotent membership lookup/upsert and uniqueness-safe persistence. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling` | Modify | Expose safe-off/readiness diagnostics while retaining claim, retry, lifecycle logging, and redaction behavior. |
| `apps/web/app/src/modules/auth/{presentation,infrastructure}`, `apps/web/app/src/router/index.ts` | Modify/Create | Invite acceptance, first-login redirect, workspace hydration, and safe expired/replayed states. |
| `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` | Create | Invitee journey through workspace, scheduler, unavailable capability, and typed failure. |
| `server/smp/src/test/{kotlin,resources/features}` and `apps/web/app/src/modules/**` tests | Modify/Create | Mandatory backend BDD plus focused unit/integration and frontend tests. |
| `infra/apps/smp/swarm/stack.yaml`, `docs/infrastructure/private-beta-launch-readiness-runbook.md` | Modify/Create | Configurable worker safe-off, managed route/readiness, backup/restore, rollback, failure/stale-job and operator procedures. |

## Interfaces / Contracts

```kotlin
data class AcceptInvitationCommand(val rawToken: String, val principalId: String?)
data class InvitationAcceptanceResult(val workspaceId: String, val membershipStatus: String)
interface WorkspaceMembershipProvisioner {
    suspend fun reconcile(workspaceId: String, principalId: String): WorkspaceMembershipSnapshot
}
```

The acceptance endpoint returns only a safe result/error; invitation summaries expose status and timestamps, never token material. Publishing responses expose canonical lifecycle states and allowlisted failure categories only.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Token lifecycle, replay, membership reconciliation, safe-off, stale/failure mapping | JUnit/Kotlin existing domain and worker tests; Vitest for stores/views and failure copy. |
| Integration/BDD | Transactional acceptance, email-verification boundary, workspace isolation, publication lifecycle and disabled worker | Add tagged Cucumber scenarios (`@smoke`, `@fast`) and Postgres/Testcontainers coverage; use WireMock for provider outcomes. |
| E2E | Invitee first login, workspace context, scheduler/composer, unavailable provider, safe failure | Focused Playwright spec with fixtures and user-visible assertions; managed-VPS run is separately classified operator evidence. |

## Migration / Rollout

Use existing invitation, membership, publication-job, and audit tables; add only uniqueness/index changes required by the membership reconcile contract. Roll out A before invitations, keep worker safe-off during deployment, enable only after DALLAY-557 minimum evidence, and rollback by disabling invitations/worker, restoring the last known-good Swarm release, and using the documented database/media backup rehearsal.

## Observability and Evidence

Reuse `PublishingLifecycleLogger` events and canonical failure taxonomy; add stale-job/operator summaries without exposing exceptions, provider bodies, credentials, storage paths, raw invitation tokens, or full invitee email addresses. Managed-VPS evidence must identify hostname, active namespace, release identity, UTC time, operator, scope, result, and limitation. Route checks cover public API plus private readiness/management access; PostgreSQL, direct origin listeners, and port 9091 must remain externally blocked.

All live publishing claims remain `USER_REPORTED_OPERATIONAL`. Code, VPS observation, provider-verified evidence, production-verified evidence, and `MULTI_USER_VERIFIED` are distinct classifications; this change produces no provider verification and must not infer it from a successful user report.

## Open Questions

- [ ] Confirm the managed beta hostname, release identifier format, and evidence retention location before the operator run.
- [ ] Confirm the approved restore target and test-data scope for the VPS rehearsal.
