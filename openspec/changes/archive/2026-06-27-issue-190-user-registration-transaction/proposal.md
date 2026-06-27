# Proposal: Atomic User Registration Reactive Transaction (Issue #190)

## Intent

User registration currently executes 8 separate DB insert statements (`principals`, `user_identities`, `local_password_credentials`, `workspaces`, `workspace_ownerships`, `workspace_memberships`, `membership_roles`, `email_verification_tokens`) across 4 gateway calls without a reactive transaction. Under auto-commit, a mid-registration failure leaves orphaned records (e.g. user principal without credentials or workspace). Wrapping all database mutations in an atomic reactive transaction via `AtomicTransactionRunner` ensures all-or-nothing consistency.

## Scope

### In Scope
- Wrap user registration DB mutations in `RegisterUserHandler` within `AtomicTransactionRunner.runAtomically`.
- Promote `AtomicTransactionRunner` to `com.profiletailors.common.domain.persistence` for cross-module consumption.
- Execute side-effects (domain event dispatch `UserRegistered` and JWT session issuance) strictly post-commit after successful transaction execution.
- Maintain fast unit tests using `NoopAtomicTransactionRunner`.
- Add integration test regression coverage verifying mid-registration transactional rollback.

### Out of Scope
- Modifying underlying R2DBC gateway SQL schema or queries.
- Refactoring `TransactionalOperator` bean setup in `PersistenceConfig`.

## Capabilities

### New Capabilities
None

### Modified Capabilities
- `registration`: Update requirements to specify atomic database transaction protection during registration and defer execution of domain events and session issuance until post-commit.

## Approach

Inject `AtomicTransactionRunner` domain port into `RegisterUserHandler`. Wrap steps 1 to 4 (`createUserIdentity`, `create` credentials, `provisionDefaultWorkspace`, `createEmailVerificationToken`) inside `transactionRunner.runAtomically`. Upon successful completion of the reactive block (transaction commit), publish `UserRegistered` event and return `AuthTokens`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` | Modified | Inject `AtomicTransactionRunner` into `RegisterUserHandler` and wrap DB operations. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/AtomicTransactionRunner.kt` | Removed | Interface promoted to `com.profiletailors.common.domain.persistence` — old file deleted, new file at `shared/common/.../persistence/AtomicTransactionRunner.kt`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt` | Modified | Update infrastructure implementation imports and package locations. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` | Modified | Pass `NoopAtomicTransactionRunner` in unit test constructions. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt` | Modified | Add rollback test case for mid-registration database failure. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Event dispatch or JWT creation triggering before DB commit | Medium | Place event publishing and session generation after `runAtomically` completes successfully. |
| Duplicate role handling (`onErrorResume`) interfering with R2DBC transaction rollback flag | Medium | Verify role insertion error handling in PostgreSQL integration tests using `TransactionalOperator`. |

## Rollback Plan

Revert git changes to `LocalAuthHandlers.kt`, restore original `AtomicTransactionRunner` location in `media` package, and rerun test suite (`./gradlew test`).

## Dependencies

- `AtomicTransactionRunner` infrastructure bean wired in Spring Boot application context (`R2dbcAtomicTransactionRunner`).

## Success Criteria

- [ ] All 8 registration DB insert operations execute inside a single reactive transaction.
- [ ] Simulated DB failure at any registration step rolls back all preceding insert operations atomically.
- [ ] Domain events and JWT token generation execute only after successful transaction commit.
- [ ] `./gradlew test` passes with 100% success.
