# Design: Accept Invitations in Registration

## Technical Approach

Keep `RegisterUserHandler` as the CQRS entry point and `platformadmin` as invitation owner. Add a read-only invitation preflight, then run one acceptance transaction that re-locks and revalidates the invitation before persistent registration mutation. The transaction performs identity/credential creation, verification-token and consent writes, invitation linkage/consumption, and invitation-selected tenancy. Session issuance and domain events remain post-commit.

Defects mapped: registration authorizes on token presence; duplicate checks and identity creation are split from invitation validation; `InvitationActivationCoordinator` owns a nested transaction and provisions before linkage; `R2dbcWorkspaceProvisioningService` relies on auto-commit; registration rejects every existing email; and the gateway and `AcceptInvitationHandler` fall back to the invitation ID as workspace ID. Acceptance has no acceptance/replay telemetry or signal.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Registration owns one transaction; coordinator participates | Requires API split | Chosen: preserves the R2DBC connection and row-lock lifetime through `AtomicTransactionRunner`. |
| Validate once, then trust result | Race-prone | Rejected: preflight is advisory; the transaction locks by candidate key and revalidates token, status, expiry, and email. |
| Client/invitation-ID workspace selection | Unsafe | Rejected: existing target uses stored `workspaceId`; new target provisions exactly once; missing resolution fails. |
| Invitation-specific identity model | Duplicates rules | Rejected: reuse identity, password, verification, consent, and existing unique constraints. |

## Data Flow

```text
HTTP /api/auth/register -> RegisterUserCommand -> policy/normalization
  -> read-only invitation preflight
  -> AtomicTransactionRunner
       -> lock/revalidate invitation -> identity + credential
       -> existing membership OR one new workspace
       -> consent + verification -> ACCEPTED linkage
  <- commit -> redacted signals -> session
```

A valid concurrent contender holds `SELECT ... FOR UPDATE` on the candidate row until commit. The loser observes the non-active lifecycle and rejects before creating an identity. Email uniqueness and membership constraints remain database backstops.

## File Changes

| File | Action | Description |
|---|---|---|
| `identity/application/LocalAuthHandlers.kt` | Modify | Preflight before mutation; orchestrate one transaction; retain pre-transaction password hashing and post-commit session/event behavior. |
| `identity/application/InvitationRegistrationGateway.kt` | Modify | Replace string/fallback contract with validated context and completion semantics. |
| `identity/application/InvitationRegistrationContext.kt` | Create | Immutable invitation ID, target, stored workspace ID, and approved source metadata; never raw token or full email. |
| `platformadmin/application/InvitationActivationCoordinator.kt` | Modify | Split lock/revalidation from completion; remove internal transaction; resolve workspace only from invitation. |
| `platformadmin/application/AcceptInvitation.kt` | Modify | Wrap authenticated acceptance in the transaction and return resolved workspace. |
| `platformadmin/infrastructure/InvitationRegistrationGatewayAdapter.kt` | Modify | Adapt the context/completion port without exposing platformadmin entities. |
| `platformadmin/application/contracts/InvitationTelemetry.kt` and `platformadmin/infrastructure/observability/InvitationObservability.kt` | Modify | Add bounded accepted, expired, and replay-rejected counters; retain revoke metric. |
| `platformadmin/domain/InvitationAccepted.kt` | Create | Redacted post-commit signal with invitation/principal/workspace IDs, target, outcome, and timestamp. |
| `platformadmin/infrastructure/PlatformAdminBootstrapConfiguration.kt` | Modify | Wire split coordinator and shared transaction/telemetry seams. |
| `platformadmin/infrastructure/http/AdminProblemDetailsHandler.kt` | Modify | Map the platformadmin-owned invitation failure taxonomy without raw token/email; keep identity advice free of platformadmin dependencies to preserve module boundaries. |
| Focused identity/platformadmin/tenancy integration tests | Modify | Add rollback, race, target-resolution, and leakage evidence. |

## Interfaces / Contracts

`InvitationRegistrationContext` carries only `invitationId`, `target`, `workspaceId?`, and approved source metadata. `prepare(rawToken, normalizedEmail)` is read-only. `complete(context, rawToken, principalId, displayName)` is callable inside the caller transaction, re-locks/revalidates, performs target-specific tenancy, accepts the aggregate, and returns `workspaceId` plus membership status. Raw token ends at the adapter boundary and is never persisted, logged, measured, or returned.

Existing identity acceptance requires an authenticated principal whose identity and normalized email match. It creates no duplicate identity or credential and applies the existing membership, consent, verification, and session policy.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Preflight, lifecycle, target resolution, email matching, no fallback | Coordinator and registration-handler fakes. |
| Integration | Failure after each mutation, rollback, unique-email race, row-lock race, one new workspace, existing-workspace membership | PostgreSQL/Testcontainers and existing transaction fixtures. |
| HTTP/BDD | Valid invite-only registration, safe rejections, response/session, workspace override, token/PII absence | WebTestClient plus `auth/registration.feature` focused scenarios. |

## Migration / Rollout

No schema migration. Existing candidate-key uniqueness, version CAS, invitation constraints, and tenancy foreign keys remain guardrails. Roll back by disabling invite-only registration with the existing fail-closed mode.

## Resolved Contract Decisions

- [x] Problem Details taxonomy: invalid `400 INVITATION_INVALID`; expired `410 INVITATION_EXPIRED`; revoked `410 INVITATION_REVOKED`; consumed `409 INVITATION_ALREADY_CONSUMED`; replay `409 INVITATION_REPLAYED` only when the domain distinguishes it, otherwise consumed classification plus `replay_rejected` telemetry; email mismatch `403 INVITATION_EMAIL_MISMATCH`; client workspace input `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED`. Details remain generic and omit sensitive data.
- [x] Existing identities may accept only with authentication and exact normalized-email match; no duplicate identity or credential is created; verification follows existing policy.
- [x] Client `workspaceId` is rejected even when it matches the invitation; it is never compared or ignored.
