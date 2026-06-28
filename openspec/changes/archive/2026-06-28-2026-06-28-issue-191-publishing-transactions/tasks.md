# Tasks: Publishing Mutation Transactions

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 550–850 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 application RED/GREEN → PR 2 PostgreSQL rollback suite |
| Delivery strategy | single-pr size:exception accepted for PR #206 |
| Chain strategy | size-exception |

Decision needed before apply: Resolved — user selected strategy 1, complete PR #206 in one PR despite 400-line budget risk.
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Framework-neutral handler tests and minimal transaction wiring | PR 1 | Base `main`; focused unit/architecture checks |
| 2 | Real PostgreSQL commit/rollback evidence | PR 2 | Base PR 1 branch; PostgreSQL verification |

## Phase 1: Application RED

- [x] 1.1 In `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt`, replace the Spring/Reactor operator mock with a recording pass-through `AtomicTransactionRunner`; make Create/Edit/Cancel/Retry/Reschedule invocation-once and Delete-never assertions fail.
- [x] 1.2 In the same file, add failing authorization/lifecycle/capability/external-read cases proving validation and reads finish before the runner and no writes occur.
- [x] 1.3 Add failing Edit/Retry/Reschedule cases whose repository returns normalized identity/workspace/status/schedule data; assert replacement jobs and results use that persisted copy.
- [x] 1.4 Confirm RED: `./gradlew :server:smp:test --tests '*PublishingHandlersTest' --no-daemon`.

## Phase 2: PostgreSQL RED

- [x] 2.1 Create `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` with `postgres` tag, Testcontainers support, Liquibase, real R2DBC repositories/runner, deterministic seeds, cleanup, and direct SQL assertions.
- [x] 2.2 Add failing Create commit/rollback tests: throw on enqueue; assert rollback removes publication, ordered asset links, and job.
- [x] 2.3 Add failing Edit commit/rollback tests: fail replacement insert via duplicate job ID; assert original fields, ordered links, and exact job survive.
- [x] 2.4 Add failing Cancel commit/rollback tests: throw on cancel; assert publication and job statuses restore.
- [x] 2.5 Add failing Retry commit/rollback tests using duplicate job ID; assert original publication and exact deleted job restore.
- [x] 2.6 Add failing Reschedule commit/rollback tests using duplicate job ID; assert original timing/assets and exact job restore.
- [x] 2.7 Confirm RED: `SMP_POSTGRES_TEST_PASSWORD=... ./gradlew :server:smp:postgresIntegrationTest --tests '*PublishingHandlersTransactionPostgresIntegrationTest' --no-daemon`.

## Phase 3: Minimal GREEN

- [x] 3.1 Modify `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt`: inject `AtomicTransactionRunner` into exactly five handlers, keep preparation outside, wrap paired writes, propagate exceptions, and remove Spring/Reactor imports/helpers plus unused Delete transaction/job wiring.
- [x] 3.2 Build Edit/Retry/Reschedule replacement jobs only after `updateEditableDraft` returns; return persisted results and run both focused commands until green.

## Phase 4: Refactor and Verify

- [x] 4.1 Refactor duplicated persisted-publication job construction in `PublishingHandlers.kt` without widening transaction boundaries; rerun focused tests.
- [x] 4.2 Run fresh architecture/unit checks: `./gradlew :server:smp:test --tests '*HexagonalArchTest' --tests '*PublishingHandlersTest' --no-daemon --rerun-tasks`.
- [x] 4.3 Run fresh focused PostgreSQL suite: `./gradlew :server:smp:postgresIntegrationTest --tests '*PublishingHandlersTransactionPostgresIntegrationTest' --no-daemon --rerun-tasks`.
- [x] 4.4 Format check: `./gradlew :server:smp:spotlessCheck --no-daemon`. Full `just backend-lint` not run during focused verification.
- [x] 4.5 Run final CI with infrastructure available: `just infra-up && just ci-full`; then `just infra-down`. Passed after exporting `SMP_POSTGRES_TEST_PASSWORD` from the local `SMP_POSTGRES_PASSWORD`, matching the CI workflow contract.
