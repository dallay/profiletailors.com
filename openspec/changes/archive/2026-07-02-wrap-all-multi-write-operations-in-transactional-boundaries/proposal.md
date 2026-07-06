# Proposal: Wrap Multi-Write Operations in Transactional Boundaries

## Intent

Three media handlers combine external S3-compatible storage operations with database writes without
transactional coordination. Since storage operations cannot participate in R2DBC transactions,
partial failures leave the system inconsistent: orphaned storage objects, leaked upload slots, or
dangling blob records with no asset reference. This change wraps the database-side writes in
`AtomicTransactionRunner` boundaries to eliminate those inconsistency windows.

## Scope

### In Scope

- `DeleteWorkspaceAssetHandler` — wrap DB soft-delete inside `runAtomically {}` with async cleanup
  fallback if storage delete succeeds but DB fails
- `UploadAssetHandler` — wrap `markAsReady() + releaseConcurrentUploadSlot()` in `runAtomically {}`
- `PutAssetHandler` — wrap `upsertBlob() + createPendingAsset()` for the `handleNewBlob` path in
  `runAtomically {}`
- Unit/integration tests for each partial-failure scenario

### Out of Scope

- Modifying storage application service behavior (only DB call ordering changes)
- Changes to existing `handleExistedBlob` path (already transactional)
- Changes to `StaleAssetReconciler` (already uses `runAtomically` correctly)
- Multi-region or distributed transaction concerns (saga/choreography)
- Performance optimization of existing code paths

## Approach

### DeleteWorkspaceAssetHandler (line 655)

**Saga with async compensation:**

1. Delete storage object first (irreversible, fire-and-forget safety)
2. Run `mediaAssetRepository.softDelete()` inside `transactionRunner.runAtomically {}`
3. If step 2 throws → schedule async blob cleanup job (retry queue) instead of failing the operation

Rationale: Storage delete is the irreversible operation; DB soft-delete is the compensatable one.

### UploadAssetHandler (line 181)

**Rate-limit check stays outside; final state update is atomic:**

- `claimConcurrentUploadSlot()` — outside (rate-limit guard, idempotent)
- `uploadWithStreamingValidation()` — outside (external S3, no DB involvement)
- `transactionRunner.runAtomically { markAsReady() + releaseConcurrentUploadSlot() }` — atomic DB
  update

Rationale: Slot claim is a pre-condition check; the atomic window only needs to cover the state
transition from UPLOADING→READY and slot release.

### PutAssetHandler (line 691 — handleNewBlob path only)

**Blob + asset creation in one transaction:**

```
transactionRunner.runAtomically {
    workspaceFileBlobRepository.upsertBlob(workspaceId, fileHash)
    createPendingAsset(command)  // mediaAssetRepository.create() inside
}
```

Note: `handleExistedBlob` already uses `runAtomically` correctly (line 781); only `handleNewBlob` →
`createPendingAsset` is broken.

## Affected Areas

| Area                                                                                                                     | Impact   | Description                                                           |
|--------------------------------------------------------------------------------------------------------------------------|----------|-----------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`                                   | Modified | Three handlers refactored to use `transactionRunner.runAtomically {}` |
| `server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaHandlersTest.kt`                               | Modified | Add partial-failure test cases for each handler                       |
| `server/smp/src/test/kotlin/com/profiletailors/smp/media/integration/MediaHandlersTransactionPostgresIntegrationTest.kt` | New      | Full Postgres integration tests for transactional boundaries          |

## Risks

| Risk                                                                    | Likelihood | Mitigation                                                                              |
|-------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------|
| Storage delete succeeds, DB soft-delete fails → orphaned storage object | Low        | Async cleanup job scheduled on failure; idempotent operation can be retried             |
| markAsReady succeeds, releaseConcurrentUploadSlot fails → leaked slot   | Low        | Slot release failure rolls back transaction → upload marked failed; cleanup job handles |
| Blob upsert succeeds, asset create fails → orphaned blob                | Low        | Transaction rollback reverts blob upsert; no orphaned state                             |
| `TransactionalOperator` bean unavailable at runtime                     | Low        | Bean is declared in `PersistenceConfig.kt`; startup validation covers this              |
| Existing tests break due to transaction boundary changes                | Medium     | Review all MediaHandlers tests; adapt mocking strategy for `transactionRunner`          |

## Rollback Plan

1. **Code rollback**: Revert `MediaHandlers.kt` to pre-change commit
2. **No DB migration needed**: Changes are at the application layer only
3. **No data migration needed**: No schema changes; existing records remain valid
4. **Feature flag fallback**: If immediate rollback needed, disable affected handlers via
   `@ConditionalOnProperty` or feature flag

## Dependencies

- `AtomicTransactionRunner` interface in
  `shared/common/src/main/kotlin/com/profiletailors/common/domain/persistence/AtomicTransactionRunner.kt` —
  already exists
- `R2dbcAtomicTransactionRunner` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt` —
  already implemented
- `TransactionalOperator` bean in
  `server/smp/src/main/kotlin/com/profiletailors/smp/config/PersistenceConfig.kt` — already declared
- `PutAssetHandler` already has `transactionRunner: AtomicTransactionRunner` injected — reuse same
  pattern

## Success Criteria

- [ ] `DeleteWorkspaceAssetHandler`: storage delete + DB soft-delete produce no orphaned records on
  partial failure
- [ ] `UploadAssetHandler`: `markAsReady` + `releaseConcurrentUploadSlot` commit or roll back
  together
- [ ] `PutAssetHandler` (`handleNewBlob`): blob + asset creation are atomic; no orphaned blob on
  asset creation failure
- [ ] Existing `handleExistedBlob` path (line 781) remains unchanged and correct
- [ ] Unit tests pass for all three handlers
- [ ] Integration tests verify partial-failure rollback behavior with Postgres
