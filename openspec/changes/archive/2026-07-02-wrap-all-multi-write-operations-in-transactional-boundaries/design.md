# Design: Wrap Multi-Write Operations in Transactional Boundaries

## Technical Approach

Three handlers perform database writes without transactional coordination around storage operations.
Since storage operations (S3 delete/upload) cannot participate in R2DBC transactions, partial
failures leave inconsistent state. The fix: wrap DB-side writes in
`AtomicTransactionRunner.runAtomically {}` and establish an async cleanup compensation path for the
one handler where storage delete fires before the transaction.

## Architecture Decisions

### Decision: Async cleanup mechanism for DeleteWorkspaceAssetHandler

**Choice**: Use the existing `BlobGarbageCollector` pattern — call
`workspaceFileBlobRepository.markReadyForGC()` inside its own `runAtomically` transaction, rather
than publishing a domain event.

**Alternatives considered**:

- Domain event `AssetDeletionFailedEvent` → listener handles cleanup: adds event infrastructure
  overhead for a single use case.
- New `StorageCleanupJob` entity + reconciler: reinventing what `BlobGarbageCollector` already does.
- In-memory queue or scheduler abstraction: no such abstraction exists in this codebase.

**Rationale**: `BlobGarbageCollector` already runs hourly, uses `FOR UPDATE SKIP LOCKED`, and
respects a 7-day retention period before physical deletion. Calling
`markReadyForGC(workspaceId, fileHash, orphanedAt)` from the compensation path is identical to what
`MediaAssetExpirationJob.scheduleBlobGCIfOrphaned()` does — same pattern, same infrastructure, zero
new components.

### Decision: markAsFailed in UploadAssetHandler catch path gets its own transaction

**Choice**: `handleUploadFailure` calls `markAssetFailed` (which calls
`mediaAssetRepository.markAsFailed`) outside any `runAtomically` wrapper because by the time
`handleUploadFailure` is entered, the outer `runAtomically` block (if any) has already rolled back.

**Rationale**: The catch block fires after `uploadWithStreamingValidation` (storage) throws OR after
`markAsReady` succeeds but `releaseConcurrentUploadSlot` throws. In both cases the outer transaction
has already rolled back. `markAsFailed` is a separate DB write that should succeed or fail
independently — it gets its own error handling (try/catch inside `markAssetFailed`).

### Decision: PutAssetHandler only wraps handleNewBlob

**Choice**: `handleNewBlob` wraps `createPendingAsset` in `transactionRunner.runAtomically`.
`handleExistedBlob` is unchanged (already correct at line 781).

**Rationale**: `handleExistedBlob` already uses `transactionRunner.runAtomically` correctly. Only
the `handleNewBlob → createPendingAsset` path is broken — `upsertBlob` (line 761) is called before
the branch, so the transaction must start there.

## Data Flow

### DeleteWorkspaceAssetHandler (after change)

```
handle(command)
  ├── storageApplicationService.delete()  ← outside transaction (irreversible)
  └── transactionRunner.runAtomically {
          mediaAssetRepository.softDelete()
      }
      ├── success → return DeleteWorkspaceAssetResult
      └── failure → markReadyForGC(workspaceId, fileHash, orphanedAt=now)  ← own transaction
                     └── BlobGarbageCollector picks it up after 7-day retention
```

### UploadAssetHandler (after change)

```
handle(command)
  ├── claimConcurrentUploadSlot()  ← outside (pre-condition, idempotent)
  ├── uploadWithStreamingValidation()  ← outside (S3 call, no DB)
  ├── transactionRunner.runAtomically {
          mediaAssetRepository.markAsReady()
          mediaRateLimitRepository.releaseConcurrentUploadSlot()
      }
  │     ├── success → return LegacyUploadAssetResult
  │     └── failure (releaseConcurrentUploadSlot throws) → transaction rolls back
  └── catch (any RuntimeException):
          handleUploadFailure()  ← own error handling + markAsFailed in separate try/catch
          finally:
              mediaRateLimitRepository.releaseConcurrentUploadSlot() ← slot ALWAYS released
```

### PutAssetHandler handleNewBlob path (after change)

```
handle(command)
  ├── validation steps (outside transaction)
  ├── workspaceFileBlobRepository.upsertBlob()  ← outside transaction
  └── transactionRunner.runAtomically {
          createPendingAsset(command)  ← mediaAssetRepository.create() inside
      }
          ├── success → return PutAssetResult.Created
          └── failure (createPendingAsset throws) → transaction rolls back
                                           └── blob upsert is also rolled back
```

## File Changes

| File                                                                                                                     | Action | Description                                                                                                                                                                        |
|--------------------------------------------------------------------------------------------------------------------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`                                   | Modify | Inject `transactionRunner` into `DeleteWorkspaceAssetHandler` and `UploadAssetHandler`; wrap DB writes in `runAtomically`; add async GC compensation for Delete failure            |
| `server/smp/src/test/kotlin/com/profiletailors/smp/media/integration/MediaHandlersTransactionPostgresIntegrationTest.kt` | Create | Postgres Testcontainers integration tests verifying rollback and async cleanup                                                                                                     |
| `server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaCasHandlersTest.kt`                            | Modify | Add unit tests using `RecordingAtomicTransactionRunner` to verify `runAtomically` is called with correct block; add `FailingAtomicTransactionRunner` for partial-failure scenarios |

## Interfaces / Contracts

### New DI dependency (added to existing constructors)

```kotlin
// DeleteWorkspaceAssetHandler — add constructor param
class DeleteWorkspaceAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val storageApplicationService: StorageApplicationService,
    private val uploadSettings: MediaUploadSettings,
    private val transactionRunner: AtomicTransactionRunner,  // NEW
) { ... }

// UploadAssetHandler — add constructor param
class UploadAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val storageApplicationService: StorageApplicationService,
    private val uploadSettings: MediaUploadSettings,
    private val principalContextProvider: PrincipalContextProvider = ...,
    private val principalIdentityLookup: PrincipalIdentityLookup = ...,
    private val emailVerificationPolicy: EmailVerificationPolicy = ...,
    private val transactionRunner: AtomicTransactionRunner,  // NEW — last to preserve binary compat
) { ... }

// PutAssetHandler — already has transactionRunner, no change needed
```

### Existing contract reused

`AtomicTransactionRunner.runAtomically<suspend () -> T>: T` — no interface change.

### Test doubles

```kotlin
// In MediaCasHandlersTest.kt — existing NoopAtomicTransactionRunner (pass-through)
private object NoopAtomicTransactionRunner : AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
}

// New: FailingAtomicTransactionRunner — throws from inside runAtomically
private class FailingAtomicTransactionRunner(
    private val failOnBlock: suspend () -> Boolean  // returns true = throw
) : AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
        if (failOnBlock()) throw InjectedTransactionFailure()
        return block()
    }
}

// New: RecordingAtomicTransactionRunner — records calls for assertion
private class RecordingAtomicTransactionRunner : AtomicTransactionRunner {
    val calls = mutableListOf<suspend () -> Unit>()
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
        calls.add(block as suspend () -> Unit)
        return block()
    }
}
```

## Testing Strategy

### Unit tests (`MediaCasHandlersTest.kt`)

| Scenario                                                                  | What to verify                                                                           |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `DeleteWorkspaceAssetHandler` — softDelete succeeds                       | `transactionRunner.runAtomically` called once with softDelete block                      |
| `DeleteWorkspaceAssetHandler` — softDelete fails, storage already deleted | `workspaceFileBlobRepository.markReadyForGC` called with correct workspaceId + fileHash  |
| `UploadAssetHandler` — happy path                                         | `transactionRunner.runAtomically` called with block containing markAsReady + releaseSlot |
| `UploadAssetHandler` — releaseSlot fails                                  | `markAsFailed` called in catch path (via `handleUploadFailure`)                          |
| `PutAssetHandler` handleNewBlob — createPendingAsset succeeds             | `transactionRunner.runAtomically` called once                                            |
| `PutAssetHandler` handleNewBlob — createPendingAsset fails                | blob not orphaned (transaction rolled back)                                              |

**Mock strategy**: `RecordingAtomicTransactionRunner` to assert call count and capture the block.
`FailingAtomicTransactionRunner` to simulate partial failure and verify compensation runs.

### Integration tests (`MediaHandlersTransactionPostgresIntegrationTest.kt`)

New file at `server/smp/src/test/kotlin/com/profiletailors/smp/media/integration/`. Follows same
`@Tag("postgres") @Testcontainers` pattern as
`PublishingHandlersTransactionPostgresIntegrationTest`.

| Scenario                                            | Setup                                                                                         | Assert                                                                      |
|-----------------------------------------------------|-----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| Delete: storage succeeds → softDelete fails         | Seed asset + blob; mock storage delete to succeed; inject `FailingRepository` into softDelete | Blob status is `READY_FOR_GC` (not orphaned); asset status is not `DELETED` |
| Upload: markAsReady succeeds → releaseSlot fails    | Seed UPLOADING asset; inject failure into `releaseConcurrentUploadSlot`                       | Asset status rolled back to `UPLOADING` (not `READY`); slot is released     |
| Put: upsertBlob succeeds → createPendingAsset fails | Seed new blob (UPLOADING); inject failure into `create()`                                     | Blob row does not exist (upsert rolled back)                                |

**Failure injection pattern**: Use `TransactionalOperator.transactional` with a `Throwable`
propagator to force rollback, or inject a `FailingMediaAssetRepository` wrapper — same pattern as
`PublishingHandlersTransactionPostgresIntegrationTest`.

## Migration / Rollout

**No migration required.** All changes are additive to existing flows:

- `DeleteWorkspaceAssetHandler`: storage delete still fires first; softDelete now in transaction;
  compensation path added.
- `UploadAssetHandler`: same control flow, just `markAsReady + releaseSlot` wrapped in
  `runAtomically`.
- `PutAssetHandler` handleNewBlob: same flow, `createPendingAsset` now in `runAtomically`.
- No schema changes, no new tables, no feature flags needed.
- `handleExistedBlob` path is unchanged — no risk to existing dedup flow.

**Deployment**: all three changes are additive and backward-compatible. Deploy in a single commit.
Rollback is a simple code revert with no data migration implications.

## Open Questions

- [ ] **DeleteWorkspaceAssetHandler async cleanup timing**: `markReadyForGC` sets
  `orphanedAt = now`, but `BlobGarbageCollector` only物理 deletes after 7-day retention. Is a 7-day
  orphaned storage window acceptable, or should we add a separate "immediate GC" path for this
  specific compensation case? The proposal says async cleanup is acceptable, so this is low risk but
  worth confirming with the team.
- [ ] **UploadAssetHandler markAsFailed in catch**: should `markAsFailed` itself also be wrapped in
  `runAtomically` for the rare case where the DB write fails? Currently it's wrapped in its own
  try/catch and logs errors rather than propagating — consistent with existing behavior.
