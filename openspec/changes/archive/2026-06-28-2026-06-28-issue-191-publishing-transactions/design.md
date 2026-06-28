# Design: Publishing Mutation Transactions

## Technical Approach

Replace PR #206’s application-layer `TransactionalOperator`/Reactor usage with the existing framework-neutral `AtomicTransactionRunner`. Inject it into exactly Create, Edit, Cancel, Retry, and Reschedule; Delete remains unchanged. All reads, authorization, lifecycle/capability checks, media resolution, and draft preparation stay outside the transaction. Only the paired durable mutations execute inside it.

## Architecture Decisions

| Decision | Alternatives | Rationale |
|---|---|---|
| Use `AtomicTransactionRunner.runAtomically` in handlers | Direct `TransactionalOperator`; new publishing abstraction | Preserves ADR-0002 and `HexagonalArchTest`, and reuses the established shared port. |
| Derive replacement jobs from the persisted draft | Build jobs from queued/prepared input | Honors repository-returned identity, workspace, timing, and future normalization/enrichment. |
| Prove rollback with real PostgreSQL/R2DBC | Mocked operator; H2 | Only the production driver, Reactor context, SQL, constraints, asset-link replacement, and delete/insert job behavior provide adequate evidence. |
| Keep the existing adapter location | Move it to shared/platform infrastructure | Relocation is unrelated cleanup and explicitly outside issue #191. |

## Data Flow and Transaction Boundaries

```text
command -> context/read/validate/resolve/prepare
        -> AtomicTransactionRunner
           -> publication repository -> job repository
           -> commit result / rollback both on exception
        -> PublicationResult
```

- **Create:** `createDraft(queued)` then `enqueue(jobFrom(created))`; return `created`.
- **Edit:** `updateEditableDraft(queued)` then `replaceForPublication(jobFrom(persisted))`; return `persisted`.
- **Cancel:** `markCancelled(cancelled.id, at)` then `cancel(cancelled.id, at)`; return the policy-produced `cancelled` after commit.
- **Retry:** `updateEditableDraft(prepared)` then `replaceForPublication(jobFrom(persisted))`; return `persisted`.
- **Reschedule:** `updateEditableDraft(rescheduled)` then `replaceForPublication(jobFrom(persisted))`; return `persisted`.

Job ID generation may remain in the application helper, but all publication-derived fields and scheduling calculations use the persisted result. Exceptions escape unchanged so the runner rolls back.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` | Modify | Replace Spring/Reactor transaction code and helper; inject runner into five handlers; remove unused Delete transaction dependency. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt` | Modify | Add framework-free recording/pass-through runner and persisted-result assertions. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` | Create | Five-flow commit/rollback tests using production repositories and runner. |

`AtomicTransactionRunner.kt`, `R2dbcAtomicTransactionRunner.kt`, `PersistenceConfig.kt`, and `PublishingApplicationConfiguration.kt` require no changes. Global component scanning already discovers the `@Component` adapter and custom `@Service` handlers; constructor injection resolves the single runner bean. No dependency or adapter relocation is included.

## Interfaces / Contracts

No new production contract. Existing contract:

```kotlin
suspend fun <T : Any> runAtomically(block: suspend () -> T): T
```

The unit fake increments invocation count and directly invokes `block`; it imports neither Spring nor Reactor.

## Testing Strategy and TDD Order

1. **Red—unit:** replace operator mock with the runner fake; assert each of five handlers enters it once, validation failures enter zero times, and Edit/Retry/Reschedule jobs use a repository-returned normalized copy.
2. **Red—PostgreSQL:** add `@Tag("postgres")`, `@Testcontainers(disabledWithoutDocker = true)` tests using `PostgresTestContainerSupport`, Liquibase, real repositories, `R2dbcTransactionManager`, `TransactionalOperator`, and `R2dbcAtomicTransactionRunner`.
3. **Green:** change handler wiring and blocks minimally; then refactor duplicate persisted-job construction.

For each flow, seed required principal/workspace/account/publication/assets/job, run a successful case proving both sides commit, then force the job-side failure. Create’s decorator throws on enqueue; Cancel’s throws on cancel. Edit/Retry/Reschedule delegate replacement with a sentinel duplicate job ID so PostgreSQL performs the real delete then fails insert. Query tables directly: Create leaves no publication, asset links, or job; Edit restores original publication fields and ordered asset links; Cancel restores publication/job statuses; Retry and Reschedule restore original publication and preserve the exact original job row after delete/insert rollback.

Focused validation:

```sh
./gradlew :server:smp:test --tests '*HexagonalArchTest' --tests '*PublishingHandlersTest' --no-daemon
SMP_POSTGRES_TEST_PASSWORD=... ./gradlew :server:smp:postgresIntegrationTest --tests '*PublishingHandlersTransactionPostgresIntegrationTest' --no-daemon
just backend-lint
```

## Migration / Rollout

No migration or feature flag required. Revert handler/test changes together if rollback is needed.

## Open Questions

None.
