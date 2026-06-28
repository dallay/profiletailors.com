# Proposal: Publishing Mutation Transactions

## Intent

Correct PR #206 while resolving issue #191: publication and delivery-job writes can currently diverge when the second write fails. Make the five mutation workflows atomic without leaking Spring/Reactor into application code or weakening persisted-result semantics.

## Scope

### In Scope
- Inject the existing framework-neutral `AtomicTransactionRunner` into Create, Edit, Cancel, Retry, and Reschedule handlers only.
- Atomically commit or roll back each publication mutation (including asset links) with its corresponding job mutation.
- Build Edit/Retry/Reschedule jobs from the persisted publication returned by the repository.
- Add pure application tests with a no-op/recording runner and real PostgreSQL rollback integration tests for all five workflows.
- Remove unused Delete transaction wiring introduced by PR #206.

### Out of Scope
- Delete-publication atomicity or behavior changes.
- Relocating `R2dbcAtomicTransactionRunner` from its current infrastructure package.
- New transaction abstractions, dependencies, endpoints, lifecycle rules, or unrelated adapter/configuration changes.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `publishing`: Create/Edit/Cancel/Retry/Reschedule publication and job mutations become atomic and preserve repository-returned publication semantics.
- `backend-postgres-testcontainers`: PostgreSQL integration coverage proves commit and rollback of publishing multi-write transactions.

## Approach

Keep validation, authorization, reads, media resolution, and pre-persistence preparation outside the transaction. Use `AtomicTransactionRunner` in the application handlers to wrap only paired persistence mutations. Retain Spring `TransactionalOperator`, R2DBC transaction management, and Reactor context in infrastructure. Test failures by decorating the real job repository to throw during the second operation, then query PostgreSQL to prove publication rows, asset links, and prior jobs remain unchanged; also verify successful paired commits.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../publishing/application/PublishingHandlers.kt` | Modified | Five atomic workflows; persisted-result job creation; no Delete wiring |
| `server/smp/.../publishing/application/PublishingHandlersTest.kt` | Modified | Framework-free runner test double |
| `server/smp/.../publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` | New | Real PostgreSQL commit/rollback coverage |
| `shared/common/.../AtomicTransactionRunner.kt` | Reused | Existing port remains unchanged |
| `server/smp/.../media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt` | Reused | Existing infrastructure adapter remains in place |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| PostgreSQL tests skipped outside full CI | Medium | Tag `postgres`; run explicit PostgreSQL suite in validation/full CI |
| Incomplete rollback assertions | Medium | Assert publication, asset links, and pre-existing job state |
| Transaction scope grows around non-writes | Low | Keep reads and validation outside runner block |

## Rollback Plan

Revert handler injection/transaction blocks and the dedicated integration suite together; the unchanged shared runner and infrastructure configuration require no rollback.

## Dependencies

- Existing `AtomicTransactionRunner`, R2DBC adapter/configuration, PostgreSQL Testcontainers support, Docker, and `SMP_POSTGRES_TEST_PASSWORD`.

## Success Criteria

- [ ] Exactly five handlers atomically commit publication+job changes and fully roll back second-write failures.
- [ ] Edit/Retry/Reschedule jobs use persisted repository results.
- [ ] Delete has no unused transaction wiring; application remains Spring/Reactor-free.
- [ ] `HexagonalArchTest`, focused publishing unit tests, and tagged PostgreSQL transaction tests pass.
