# Delta for Media Library — Transactional Boundaries for Multi-Write Handlers

> **Change:** wrap-all-multi-write-operations-in-transactional-boundaries
> **Domain:** media-library
> **Status:** Draft for Implementation

## Context

Three media handlers combine external S3-compatible storage operations with database writes without transactional coordination. Since storage operations cannot participate in R2DBC transactions, partial failures leave the system inconsistent: orphaned storage objects, leaked upload slots, or dangling blob records. This delta adds transactional boundaries to eliminate those inconsistency windows.

---

## ADDED Requirements

### Requirement: DeleteWorkspaceAssetHandler — Storage Delete + DB Soft-Delete in Atomic Transaction

The system MUST wrap the `mediaAssetRepository.softDelete()` call inside `transactionRunner.runAtomically {}` after the storage delete succeeds, so that DB soft-delete commits or rolls back as an atomic unit.

The storage delete operation MUST be executed first and outside the transaction because it is the irreversible operation. If the storage delete succeeds but the DB soft-delete fails, the system MUST schedule an asynchronous blob cleanup job rather than propagating the failure to the caller.

The async cleanup job MUST be idempotent and MUST NOT block the delete response from returning successfully to the caller once the storage object has been deleted.

#### Scenario: Storage delete and DB soft-delete both succeed

- GIVEN an authenticated workspace member deletes an existing `READY` media asset
- WHEN `DeleteWorkspaceAssetHandler` executes
- THEN `storageApplicationService.delete()` MUST be called first
- AND if storage delete succeeds, `transactionRunner.runAtomically { mediaAssetRepository.softDelete() }` MUST be executed
- AND the asset MUST be soft-deleted in the database
- AND the response MUST indicate deletion success

#### Scenario: Storage delete succeeds but DB soft-delete fails — async cleanup scheduled

- GIVEN an authenticated workspace member deletes an existing `READY` media asset
- AND the storage delete completes successfully
- WHEN `mediaAssetRepository.softDelete()` throws inside `runAtomically {}`
- THEN the transaction MUST be rolled back
- AND an async blob cleanup job MUST be scheduled with sufficient context (assetId, storageKey, workspaceId)
- AND the response MUST indicate deletion success (storage is already deleted)

#### Scenario: Storage delete fails — operation fails without DB change

- GIVEN an authenticated workspace member deletes an existing `READY` media asset
- WHEN `storageApplicationService.delete()` throws
- THEN the handler MUST NOT call `softDelete()`
- AND the asset MUST remain in its current database state
- AND the error MUST be propagated to the caller

---

### Requirement: UploadAssetHandler — Atomic State Transition and Slot Release

The system MUST wrap `mediaAssetRepository.markAsReady()` and `mediaRateLimitRepository.releaseConcurrentUploadSlot()` inside `transactionRunner.runAtomically {}` so that both DB updates commit or roll back together.

The slot claim (`claimConcurrentUploadSlot()`) MUST remain outside the transaction because it is a pre-condition check and is idempotent. The S3 upload (`uploadWithStreamingValidation()`) MUST remain outside the transaction because it is an external operation with no DB involvement.

If the transaction rolls back, the upload MUST be considered failed and the cleanup semantics defined in the media-library spec apply.

#### Scenario: Upload completes and both markAsReady and slot release commit atomically

- GIVEN an asset is in `UPLOADING` status with a valid upload slot claimed
- AND the S3 upload via `uploadWithStreamingValidation()` completes successfully
- WHEN the atomic block executes `{ markAsReady(); releaseConcurrentUploadSlot() }`
- THEN both DB updates MUST commit together
- AND the asset MUST transition to `READY`
- AND the concurrent upload slot MUST be released

#### Scenario: markAsReady succeeds but releaseConcurrentUploadSlot fails — transaction rolls back

- GIVEN an asset is in `UPLOADING` status with a valid upload slot claimed
- AND `markAsReady()` succeeds inside the atomic block
- WHEN `releaseConcurrentUploadSlot()` throws
- THEN the transaction MUST roll back
- AND `markAsReady()` MUST be reverted
- AND the asset MUST remain in `UPLOADING` status
- AND the slot MUST NOT be released (it remains held by the asset)

#### Scenario: Slot claim stays outside transaction (pre-condition)

- GIVEN a workspace has 5 uploads in progress
- WHEN a sixth upload request is made
- THEN `claimConcurrentUploadSlot()` MUST be evaluated outside any transaction
- AND the request MUST be rejected with HTTP 429 before any atomic block is entered

---

### Requirement: PutAssetHandler — Atomic Blob and Asset Creation for handleNewBlob Path

The system MUST wrap `workspaceFileBlobRepository.upsertBlob()` and `createPendingAsset()` (which includes `mediaAssetRepository.create()`) inside `transactionRunner.runAtomically {}` for the `handleNewBlob` code path only.

If the blob upsert succeeds but the asset creation fails, the transaction MUST roll back and revert the blob upsert, preventing orphaned blob records.

The `handleExistedBlob` path (line 781) already uses `transactionRunner.runAtomically {}` correctly and MUST NOT be modified.

#### Scenario: New blob and new asset both commit atomically

- GIVEN no blob exists for `(workspaceId, fileHash)`
- WHEN a client calls `PUT /api/workspaces/{workspaceId}/media/assets/{assetId}` with new file content
- THEN `transactionRunner.runAtomically { upsertBlob(); createPendingAsset() }` MUST be executed
- AND the blob row MUST be inserted with status `UPLOADING`
- AND the asset row MUST be inserted with status `PENDING_UPLOAD`
- AND both inserts MUST commit together

#### Scenario: Blob upsert succeeds but asset creation fails — blob upsert rolled back

- GIVEN no blob exists for `(workspaceId, fileHash)`
- AND `workspaceFileBlobRepository.upsertBlob()` succeeds inside the atomic block
- WHEN `createPendingAsset()` throws
- THEN the transaction MUST roll back
- AND the blob upsert MUST be reverted
- AND no orphaned blob record MUST exist

#### Scenario: handleExistedBlob path unchanged

- GIVEN `PutAssetHandler` detects an existing blob for `(workspaceId, fileHash)` with status `READY`
- WHEN the `handleExistedBlob` path is executed
- THEN `transactionRunner.runAtomically {}` MUST already be in use at that code path
- AND this change MUST NOT modify that existing transactional behavior

---

## MODIFIED Requirements

### Requirement: Upload Retry After Failed Atomic Block

(Previously: Upload retry allowed when asset status is `PROCESSING` or `FAILED`)

When an atomic block (`markAsReady()` + `releaseConcurrentUploadSlot()`) rolls back due to a partial failure, the asset remains in its pre-upload status. The system MUST allow the client to retry the upload, subject to the same concurrency and rate-limit checks as a fresh upload.

#### Scenario: Atomic block rolls back — client retries upload

- GIVEN an asset is in `UPLOADING` status after a partial failure rolled back `markAsReady()`
- WHEN the client retries the upload
- THEN the system MUST allow the retry if the asset status is `UPLOADING` and the slot is still held
- AND the concurrent upload slot claimed at the start of the original upload attempt remains valid

---

## REMOVED Requirements

None.

---

## Non-Functional Requirements

### Rollback Behavior

| Operation | What Rolls Back | What Does NOT Roll Back |
|-----------|-----------------|------------------------|
| `DeleteWorkspaceAssetHandler` | DB soft-delete (if scheduled cleanup succeeds) | Storage delete (irreversible) |
| `UploadAssetHandler` | `markAsReady()` | Slot claim (idempotent pre-condition) |
| `PutAssetHandler` `handleNewBlob` | Blob upsert + asset creation | Nothing (all DB) |

### Async Cleanup Semantics

When `DeleteWorkspaceAssetHandler` schedules async cleanup after a partial failure:
- The job MUST be enqueued with `(assetId, workspaceId, storageKey)` context
- The job MUST be idempotent — safe to execute multiple times
- The job MUST be retriable with backoff on transient failures
- Cleanup failure MUST be logged with sufficient context for the stale reconciler to pick up

### Constraints — What MUST NOT Change

1. The `handleExistedBlob` path at line 781 in `PutAssetHandler` MUST NOT be modified — it already correctly uses `transactionRunner.runAtomically {}`
2. Storage application service behavior MUST NOT change — only the DB call ordering changes
3. `claimConcurrentUploadSlot()` MUST remain outside the transaction as a pre-condition guard
4. `uploadWithStreamingValidation()` MUST remain outside the transaction — S3 operations cannot participate in R2DBC transactions
5. No schema changes — all changes are at the application layer only
6. The `StaleAssetReconciler` behavior is out of scope and already uses `runAtomically` correctly

---

## Integration Test Scenarios

The integration test suite MUST verify the following with a real Postgres database:

### DeleteWorkspaceAssetHandler
- [ ] Storage delete succeeds + DB soft-delete succeeds → asset is `DELETED`
- [ ] Storage delete succeeds + DB soft-delete fails → cleanup job is scheduled, response is success
- [ ] Storage delete fails → `DELETED` status is NOT set, error is propagated

### UploadAssetHandler
- [ ] Happy path: `markAsReady()` + `releaseConcurrentUploadSlot()` both succeed and commit
- [ ] `markAsReady()` succeeds, `releaseConcurrentUploadSlot()` fails → transaction rolls back, asset stays `UPLOADING`
- [ ] Slot claim outside transaction: 6th concurrent upload is rejected before entering atomic block

### PutAssetHandler (handleNewBlob only)
- [ ] New blob + new asset both commit atomically
- [ ] Blob upsert succeeds + asset creation fails → blob upsert is rolled back, no orphaned blob
- [ ] `handleExistedBlob` path (line 781) is NOT affected by the change
