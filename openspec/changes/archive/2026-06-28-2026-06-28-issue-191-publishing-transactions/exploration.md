## Exploration: Publishing Mutation Transactions (Issue #191 / PR #206)

### Current State
The five publishing mutation handlers perform publication and job writes sequentially with no shared transaction on `main`: Create inserts/upserts the publication and asset links, then enqueues a job; Edit, Retry, and Reschedule update the publication and asset links, then delete/insert the job; Cancel updates the publication, then updates the job. A failure in the second repository leaves inconsistent durable state.

PR #206 attempts to add atomicity by injecting Spring `TransactionalOperator` and using Reactor `mono`/`awaitSingle` directly in `PublishingHandlers.kt`. This is not mergeable as designed: it violates the accepted hexagonal boundary and the explicit `HexagonalArchTest` rule forbidding Reactor and coroutine-Reactor dependencies in application packages. The targeted architecture test was run on the PR branch and failed at `applicationLayerShouldNotDependOnReactorOrCoroutinesReactor`.

Main already contains the reusable `com.profiletailors.common.domain.persistence.AtomicTransactionRunner` port and `R2dbcAtomicTransactionRunner` adapter from PR #205. `PersistenceConfig` supplies `R2dbcTransactionManager` and `TransactionalOperator`; component scanning discovers the adapter. Publishing handlers should consume the port, not Spring/Reactor. No publishing-specific transaction bean is required, but constructor injection must be updated for exactly Create/Edit/Cancel/Retry/Reschedule. PR #206 unnecessarily adds the operator to Delete without using it.

PR #206 also claims rollback integration tests but adds none. It only mocks `TransactionalOperator` as a pass-through in the existing unit suite, which cannot prove rollback. Its Edit/Retry/Reschedule jobs are constructed from the pre-persisted draft (`queued`, `prepared`, `rescheduled`) instead of the repository result. Today the R2DBC repository returns the same object, so the regression is latent, but it weakens the repository contract and can produce stale job identity/workspace/scheduling data if persistence normalizes or enriches a draft later.

### Affected Areas
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` — five handlers need `AtomicTransactionRunner`; all validation/media resolution must remain outside the transaction; each publication+job mutation pair must run inside it; jobs must be derived from the persisted draft.
- `shared/common/src/main/kotlin/com/profiletailors/common/domain/persistence/AtomicTransactionRunner.kt` — existing framework-neutral port from PR #205; reuse unchanged.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt` — existing R2DBC adapter; despite its media package location, it is globally component-scanned and implements the shared port.
- `server/smp/src/main/kotlin/com/profiletailors/smp/config/PersistenceConfig.kt` — already provides the reactive transaction manager/operator required by the adapter; no new transaction mechanism should be introduced.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/PublishingApplicationConfiguration.kt` — only potential explicit wiring location; normal constructor injection should work through the custom `@Service` component scan and the existing runner bean, so additional beans are likely unnecessary.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` — real multi-statement behavior under test: publication upsert plus asset-link replacement; job replacement delete plus insert; cancellation updates by publication ID.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt` — use a small no-op/recording `AtomicTransactionRunner`, preserving fast pure application tests and avoiding MockK/Reactor leakage.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` — recommended dedicated PostgreSQL rollback suite for all five handlers using real R2DBC repositories and the real transaction runner.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/PostgresTestContainerSupport.kt` — existing container, Liquibase baseline, and cleanup support should be reused.
- `server/smp/src/test/kotlin/com/profiletailors/smp/HexagonalArchTest.kt` — existing enforcement proves PR #206's direct Reactor dependency is invalid.
- `server/smp/build.gradle.kts` — PR #206's new MockK dependency is redundant for this change because MockK is already broadly used elsewhere only after the branch addition; a hand-written no-op runner needs no new dependency.

### Approaches
1. **Reuse `AtomicTransactionRunner` and test through real PostgreSQL R2DBC (Recommended)** — inject the shared port into the five handlers, wrap only database mutation pairs, and exercise each handler against real repositories with a failing job-repository decorator inside the real runner.
   - Pros: Preserves hexagonal boundaries; reuses PR #205's standard; validates actual Reactor context/connection rollback; covers PostgreSQL SQL and asset-link/job replacement behavior; keeps unit tests framework-free.
   - Cons: Requires careful fixture setup for handler authorization, lifecycle state, accounts, and five failure scenarios; PostgreSQL tests need Docker and the configured test password.
   - Effort: Medium

2. **Reuse `AtomicTransactionRunner` with H2 integration tests** — same production design, but test rollback using an H2 R2DBC connection and minimal schema.
   - Pros: Faster and Docker-free; proves basic transaction propagation.
   - Cons: Duplicates schema/fixtures and cannot guarantee PostgreSQL semantics for production SQL, constraints, timestamps, and delete/insert behavior; weaker evidence for a critical persistence bug.
   - Effort: Medium

3. **Keep PR #206's direct `TransactionalOperator` design** — wire Spring/Reactor into application handlers and mock it in unit tests.
   - Pros: Small apparent diff.
   - Cons: Fails the repository's architecture test; duplicates the abstraction already standardized by PR #205; couples tests to Reactor/Spring; current tests do not prove rollback; includes unused Delete wiring.
   - Effort: Low, but unacceptable

### Recommendation
Replace PR #206's direct Spring/Reactor use with the existing shared `AtomicTransactionRunner`. Inject it into exactly Create, Edit, Cancel, Retry, and Reschedule. Keep all reads, authorization, lifecycle validation, capability checks, media resolution, and job-object preparation that does not require persistence outside the transaction; inside, perform only the publication mutation and corresponding job mutation. For Edit/Retry/Reschedule, construct the replacement job from the `persisted` draft returned by `updateEditableDraft`, or build it inside the atomic block after persistence, preserving repository semantics.

Add one dedicated `@Tag("postgres")` integration test class using `PostgresTestContainerSupport`, Liquibase, `R2dbcPublicationRepository`, `R2dbcPublicationJobRepository`, `R2dbcTransactionManager`, and the real `R2dbcAtomicTransactionRunner`. For each handler, delegate the job port to the real repository but throw at the intended second operation; then query tables directly to prove the publication row/status/asset links and pre-existing job are unchanged. Also include success-path coverage that proves both sides commit. Keep existing handler unit tests with a hand-written no-op or recording runner. Run `HexagonalArchTest`, focused publishing unit tests, and the tagged PostgreSQL rollback suite.

### Risks
- PostgreSQL rollback tests are tagged and `disabledWithoutDocker`; if CI does not execute the postgres task with `SMP_POSTGRES_TEST_PASSWORD`, critical rollback coverage could silently be skipped.
- Create/Edit publication writes include asset-link mutations, so assertions must verify links as well as the publication row; checking only publication count/status is incomplete.
- Edit/Retry/Reschedule `replaceForPublication` deletes the prior job before inserting the replacement; rollback tests must assert the original job survives a forced insert failure.
- Cancel currently passes a publication ID to a repository method named `cancel(jobId, ...)`; implementation treats it as `publication_id`. Tests should lock in the actual semantic contract or a later rename should clarify it.
- The shared R2DBC adapter lives under the media infrastructure package while serving multiple bounded contexts. It works through global component scanning, but its location is misleading and could become a modularity boundary problem; relocation is broader than Issue #191 and should be handled separately unless Spring Modulith rejects it.
- PR #206's pre-persisted job construction is presently masked because `R2dbcPublicationRepository` returns the input object. Leaving it would create a future semantic regression when persistence-generated or normalized values are introduced.

### Ready for Proposal
Yes — propose replacing PR #206's framework-coupled implementation with shared-port orchestration, real PostgreSQL rollback tests for all five handlers, persisted-result job construction, and no production changes outside the issue scope.
