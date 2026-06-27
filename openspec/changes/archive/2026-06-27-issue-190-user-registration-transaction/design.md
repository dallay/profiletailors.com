# Design: Atomic User Registration Reactive Transaction

## Technical Approach

Promote `AtomicTransactionRunner` from the media module into a shared domain-port package so `RegisterUserHandler` can coordinate registration writes without depending on Spring transaction APIs. The handler will wrap identity creation, password credential creation, default workspace provisioning, and email verification token persistence in one `runAtomically` block, then publish `UserRegistered` and issue the auth session only after the block returns successfully, matching `specs/registration/spec.md`.

## Architecture Decisions

| Decision | Alternatives considered | Rationale |
|---|---|---|
| Use shared `AtomicTransactionRunner` port from application layer | Inject `TransactionalOperator` directly; collapse writes into one SQL script | Keeps hexagonal boundaries intact, reuses existing R2DBC transaction implementation, and preserves fast unit testing with a no-op stub. |
| Keep `UserRegistered` publication and JWT/refresh issuance outside the transaction | Publish/issue inside the transaction | Prevents irreversible side effects for rolled-back registrations and aligns with the spec's post-commit requirement. |
| Preserve current gateway/service decomposition inside one transaction | Merge identity + tenancy writes into one adapter | Minimizes refactor risk and follows current bounded-context ownership while still achieving atomic persistence. |
| Add integration regression coverage around rollback and post-failure side effects | Rely only on unit tests | Transaction semantics and `onErrorResume` behavior must be proven against the real reactive stack. |

## Data Flow

```text
RegisterUserHandler
  │ validate + duplicate checks
  ▼
AtomicTransactionRunner.runAtomically
  ├─ IdentityRegistrationGateway.createUserIdentity
  ├─ LocalPasswordCredentialGateway.create
  ├─ WorkspaceProvisioningService.provisionDefaultWorkspace
  └─ IdentityRegistrationGateway.createEmailVerificationToken
        │
        ├─ success → commit
        └─ exception → rollback all prior writes

after commit only:
  RegisterUserHandler
  ├─ EventPublisher.publish(UserRegistered)
  └─ issueAuthSession(JWT + refresh session)
```

### Failure Handling

- Any uncaught exception inside `runAtomically` MUST abort the registration and roll back all prior inserts.
- `UserRegistered` and refresh-session creation MUST NOT run when the transaction fails.
- The existing `membership_roles` duplicate-swallowing path in `R2dbcWorkspaceProvisioningService` needs regression coverage to confirm an internally handled error does not leave the reactive transaction marked rollback-only unexpectedly.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `shared/common/src/main/kotlin/com/profiletailors/common/domain/persistence/AtomicTransactionRunner.kt` | Create | Shared application port for atomic transaction orchestration. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` | Modify | Inject transaction runner, move registration writes into one atomic block, keep event/session after commit. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt` | Modify | Repoint implementation to shared port package. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` | Modify | Provide `NoopAtomicTransactionRunner` and assert post-commit orchestration expectations. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt` | Modify | Add rollback regression and side-effect absence assertions. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/R2dbcWorkspaceProvisioningService.kt` | Verify only | No planned logic change, but transaction-sensitive duplicate-role behavior must be covered. |

## Interfaces / Contracts

```kotlin
package com.profiletailors.common.domain.persistence

interface AtomicTransactionRunner {
    suspend fun <T : Any> runAtomically(block: suspend () -> T): T
}
```

`RegisterUserHandler.handle()` contract changes:
- Persistent registration mutations execute inside `runAtomically`.
- Returned value remains `LocalAuthSessionResult`.
- Event publication and refresh-session persistence happen only after successful commit.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Handler orchestration order and dependency wiring | Use `NoopAtomicTransactionRunner` plus fakes/recorders to verify atomic block wraps writes and side effects happen after returned transactional result. |
| Integration | Full rollback on mid-registration failure | Inject a deterministic failing test double at one in-transaction step, POST `/api/auth/register`, then assert all affected tables remain empty. |
| Integration | Post-commit-only side effects | Assert failed registration creates no refresh session and no persisted registration rows; successful registration still returns JWT + refresh cookie. |
| E2E | None | Existing endpoint integration coverage is sufficient for this backend change. |

## Migration / Rollout

No migration required.

## Open Questions

- [ ] Should the shared transaction port live in `shared/common` immediately, or stay temporarily inside `server/smp` under a common package until more modules consume it?
- [ ] What is the safest test seam for forcing an in-transaction failure without weakening production wiring?
