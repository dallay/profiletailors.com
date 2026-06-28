## Exploration: User Registration Reactive Transaction Protection (Issue #190)

### Current State
User registration is initiated via `RegisterUserCommand` and handled by `RegisterUserHandler` located in `com.profiletailors.smp.identity.application.LocalAuthHandlers.kt`. Upon execution, the handler invokes multiple collaborator gateways and services sequentially:
1. `identityRegistrationGateway.createUserIdentity(...)` — executes 2 separate R2DBC SQL `INSERT` statements (`principals` and `user_identities`).
2. `localPasswordCredentialGateway.create(...)` — executes 1 R2DBC SQL `INSERT` statement (`local_password_credentials`).
3. `workspaceProvisioningService.provisionDefaultWorkspace(...)` — executes 4 R2DBC SQL `INSERT` statements (`workspaces`, `workspace_ownerships`, `workspace_memberships`, and `membership_roles`).
4. `identityRegistrationGateway.createEmailVerificationToken(...)` — executes 1 R2DBC SQL `INSERT` statement (`email_verification_tokens`).

Each of these 8 database operations calls `.fetch().rowsUpdated().awaitSingle()` independently outside any reactive transactional operator. Because R2DBC defaults to auto-commit when unmanaged, every statement commits immediately upon execution. A failure at any step during registration (e.g., database constraint violation during workspace creation or verification token insertion) leaves orphaned data in preceding tables (e.g. user principal without credentials or workspace).

Furthermore, audit findings (Issue #195) established that 3 incompatible transaction mechanisms coexist in the backend (`TransactionalOperator`, declarative `@Transactional` which fails on final Kotlin classes marked with custom `@Service`, and manual JDBC/R2DBC connection handling). The repository standard established in `PersistenceConfig.kt` and `media` module requires using programmatic `TransactionalOperator` via a clean application domain port (`AtomicTransactionRunner`).

### Affected Areas
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` — `RegisterUserHandler` must consume `AtomicTransactionRunner` to wrap the registration mutation sequence in a reactive transaction.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/AtomicTransactionRunner.kt` (or promoted common location `server/smp/src/main/kotlin/com/profiletailors/common/domain/persistence/AtomicTransactionRunner.kt`) — Move/expose domain port for cross-module transactional operations.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt` (or promoted infrastructure location) — Infrastructure implementation wrapping `TransactionalOperator`.
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` — Update unit tests to pass `NoopAtomicTransactionRunner`.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt` (or dedicated transaction test) — Add regression test verifying mid-registration error rolls back all database modifications atomically.

### Approaches
1. **Application-Layer Domain Port via `AtomicTransactionRunner` (Recommended)**
   - **Description**: Promote `AtomicTransactionRunner` from the `media` package to `com.profiletailors.common.domain.persistence` (or inject the infrastructure runner directly in identity configuration if keeping scope minimal). `RegisterUserHandler` receives `AtomicTransactionRunner` and wraps steps 1 through 4 inside `transactionRunner.runAtomically { ... }`. Domain event publication (`eventPublisher.publish`) and JWT session issuance occur after successful commit.
   - **Pros**: Maintains clean hexagonal architecture (no Spring/Reactor types in application handlers), integrates seamlessly with existing unit test fakes (`NoopAtomicTransactionRunner`), aligns with repo rules for Issue #195 standardization.
   - **Cons**: Requires moving or referencing `AtomicTransactionRunner`.
   - **Effort**: Low

2. **Inject `TransactionalOperator` directly into `RegisterUserHandler`**
   - **Description**: Inject Spring's `TransactionalOperator` directly into `RegisterUserHandler` and call `transactionalOperator.transactional(mono { ... }).awaitSingle()`.
   - **Pros**: Avoids touching alternative modules or moving files.
   - **Cons**: Breaks hexagonal architecture by leaking Spring Framework and Reactor infrastructure dependencies directly into application handlers; complicates unit testing with mock Spring/Reactor objects.
   - **Effort**: Low

3. **Multi-Statement Compound SQL Query within R2DBC Gateway**
   - **Description**: Combine all identity and workspace creation into a single raw SQL block/script.
   - **Pros**: Low overhead.
   - **Cons**: Breaks domain boundaries (combining identity management and workspace tenancy into one raw SQL script) and violates CQRS separation.
   - **Effort**: High

### Recommendation
Adopt **Approach 1 (Application-Layer Domain Port via `AtomicTransactionRunner`)**. Promote or share `AtomicTransactionRunner` so that `RegisterUserHandler` delegates atomicity to the infrastructure via `AtomicTransactionRunner.runAtomically`. Keep unit tests fast using `NoopAtomicTransactionRunner`, and validate rollback behavior using PostgreSQL-backed integration tests.

### Risks
- **Event Dispatching / Side-Effects inside Transaction**: Emitting `UserRegistered` domain events or issuing authentication sessions within the transactional block could cause premature side effects if the transaction later rolls back. Recommendation: Event publication and session issuance must occur strictly *after* `runAtomically` successfully returns.
- **Role Assignment Exception Trapping**: In `R2dbcWorkspaceProvisioningService`, step 4 (inserting membership roles) uses `.onErrorResume { Mono.just(0) }` to ignore duplicate keys. When executed within a reactive transaction managed by `TransactionalOperator`, an exception caught inside `.onErrorResume` might still mark the R2DBC connection/transaction as rollback-only depending on driver signals. Verification must test duplicate key conflict handling under an active `TransactionalOperator`.

### Ready for Proposal
Yes — complete analysis performed. Ready for `sdd-propose` to write the formal proposal artifact.
