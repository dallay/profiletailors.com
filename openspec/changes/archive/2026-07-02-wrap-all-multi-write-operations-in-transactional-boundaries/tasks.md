# Tasks: Wrap Multi-Write Operations in Transactional Boundaries

## Review Workload Forecast

| Field                   | Value       |
|-------------------------|-------------|
| Estimated changed lines | ~300–400    |
| 400-line budget risk    | Medium      |
| Chained PRs recommended | No          |
| Suggested split         | Single PR   |
| Delivery strategy       | ask-on-risk |
| Chain strategy          | pending     |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                | Likely PR | Notes                                                            |
|------|---------------------|-----------|------------------------------------------------------------------|
| 1    | Full implementation | PR 1      | All DI, handler changes, unit tests, integration tests in one PR |

---

## Phase 1: DI Injection

### TASK-1.1: Inject deps into DeleteWorkspaceAssetHandler

**Type:** impl ✅ COMPLETE
**Files:** `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`

Add `transactionRunner: AtomicTransactionRunner` and
`workspaceFileBlobRepository: WorkspaceFileBlobRepository` as constructor params (after existing
params to preserve binary compat). Import `AtomicTransactionRunner` (already present) and `Instant`.

Current constructor (line 655):

```kotlin
class DeleteWorkspaceAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val storageApplicationService: StorageApplicationService,
    private val uploadSettings: MediaUploadSettings,
) : CommandWithResultHandler<DeleteWorkspaceAssetCommand, DeleteWorkspaceAssetResult> {
```

Change to:

```kotlin
class DeleteWorkspaceAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val storageApplicationService: StorageApplicationService,
    private val uploadSettings: MediaUploadSettings,
    private val transactionRunner: AtomicTransactionRunner,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
) : CommandWithResultHandler<DeleteWorkspaceAssetCommand, DeleteWorkspaceAssetResult> {
```

**Verify:** `just backend-build` compiles without errors on `MediaHandlers.kt`.

---

### TASK-1.2: Inject transactionRunner into UploadAssetHandler

**Type:** impl ✅ COMPLETE
**Files:** `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`

Add `transactionRunner: AtomicTransactionRunner` as the last constructor param of
`UploadAssetHandler` (after `emailVerificationPolicy`).

Current constructor (line 181):

```kotlin
class UploadAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val storageApplicationService: StorageApplicationService,
    private val uploadSettings: MediaUploadSettings,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<LegacyUploadAssetCommand, LegacyUploadAssetResult> {
```

Change to add `transactionRunner: AtomicTransactionRunner` at the end (before `)`).

**Verify:** `just backend-build` compiles without errors on `MediaHandlers.kt`.

---

## Phase 2: Handler Implementation

### TASK-2.1: Wrap softDelete in runAtomically + async GC compensation (DeleteWorkspaceAssetHandler)

**Type:** impl ✅ COMPLETE
**Files:** `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`

Replace the `handle` method body (lines 660–685) with the transactional flow:

```kotlin
override suspend fun handle(command: DeleteWorkspaceAssetCommand): DeleteWorkspaceAssetResult {
    val asset = mediaAssetRepository.findByWorkspaceAndId(command.workspaceId, command.assetId)
        ?: throw AssetNotFoundException(command.assetId)

    asset.storageKey?.let { storageKey ->
        runCatching {
            storageApplicationService.delete(
                bucket = uploadSettings.storageBucket,
                key = storageKey,
                deleterId = command.workspaceId,
            )
        }.getOrElse { cause ->
            throw MediaServiceUnavailableException(
                "Storage deletion failed for asset ${command.assetId}",
                cause,
            )
        }
    }

    try {
        transactionRunner.runAtomically {
            mediaAssetRepository.softDelete(command.assetId, command.workspaceId)
        }
    } catch (e: RuntimeException) {
        // Storage already deleted — schedule async cleanup in its own transaction
        transactionRunner.runAtomically {
            workspaceFileBlobRepository.markReadyForGC(
                command.workspaceId,
                asset.fileHash ?: return@runAtomically,
                Instant.now(),
            )
        }
    }

    return DeleteWorkspaceAssetResult(
        assetId = command.assetId,
        workspaceId = command.workspaceId,
        deleted = true,
    )
}
```

Note: `Instant` is already imported (line 29). The `fileHash` is accessed from the already-fetched
`asset` — if null, the `markReadyForGC` call is skipped (idempotent since no blob to clean).

**Verify:** `just backend-build` compiles. `just backend-test-fast` passes
`DeleteWorkspaceAssetHandler` scenarios.

---

### TASK-2.2: Wrap markAsReady + releaseConcurrentUploadSlot in runAtomically (UploadAssetHandler)

**Type:** impl ✅ COMPLETE
**Files:** `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`

Find the current handle body in `UploadAssetHandler` (lines 214–264). Replace the `markAsReady` +
`return` block with a transactional block. The `try/catch/finally` structure is preserved; only the
`markAsReady` call moves inside `runAtomically`.

Current (lines 239–263):

```kotlin
val updated = mediaAssetRepository.markAsReady(assetId, workspaceId, fileSize)
    ?: throw IllegalStateException("Asset not found after upload: $assetId")
// ... logging ...
return LegacyUploadAssetResult(...)

} catch (e: RuntimeException) {
    handleUploadFailure(e, assetId, workspaceId)
    throw e
} finally {
    mediaRateLimitRepository.releaseConcurrentUploadSlot(workspaceId)
}
```

Replace `mediaAssetRepository.markAsReady(...)` through `return LegacyUploadAssetResult(...)` with:

```kotlin
val updated = transactionRunner.runAtomically {
    mediaAssetRepository.markAsReady(assetId, workspaceId, fileSize)
        ?: throw IllegalStateException("Asset not found after upload: $assetId")
}
// ... keep the duration logging + return LegacyUploadAssetResult(...) — outside transaction ...
return LegacyUploadAssetResult(...)
```

The `catch` and `finally` blocks are unchanged. The `finally` block's `releaseConcurrentUploadSlot`
stays as-is — it is always called to release the slot regardless of success or failure (safety net;
on the success path it becomes a no-op since the transaction committed and the slot was released
inside the atomic block, but calling it again in finally is safe since it is idempotent).

**Verify:** `just backend-build` compiles. `just backend-test-fast` passes `UploadAssetHandler`
scenarios.

---

### TASK-2.3: Wrap createPendingAsset in runAtomically (PutAssetHandler handleNewBlob path)

**Type:** impl ✅ COMPLETE
**Files:** `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`

Find `handleNewBlob` at line 870. Currently:

```kotlin
private suspend fun handleNewBlob(command: PutAssetCommand): PutAssetResult = createPendingAsset(command)
```

Replace with:

```kotlin
private suspend fun handleNewBlob(command: PutAssetCommand): PutAssetResult =
    transactionRunner.runAtomically { createPendingAsset(command) }
```

`handleExistedBlob` (line 774) is NOT modified — it already uses `transactionRunner.runAtomically`
correctly.

**Verify:** `just backend-build` compiles. `just backend-test-fast` passes `PutAssetHandler` CAS
dedup scenarios.

---

## Phase 3: Unit Tests

### TASK-3.1: Add FailingAtomicTransactionRunner and RecordingAtomicTransactionRunner test doubles

**Type:** unit-test ✅ COMPLETE
**Files:**
`server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaCasHandlersTest.kt`

Add after the existing `NoopAtomicTransactionRunner` definition (after line 791):

```kotlin
/**
 * AtomicTransactionRunner that records every block passed to runAtomically
 * so tests can assert call count and execution.
 */
private class RecordingAtomicTransactionRunner : AtomicTransactionRunner {
    val calls = mutableListOf<suspend () -> Unit>()
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
        calls.add(block as suspend () -> Unit)
        return block()
    }
}

/**
 * AtomicTransactionRunner that throws InjectedTransactionFailure when failOnBlock returns true.
 * Used to simulate partial failures inside runAtomically.
 */
private class FailingAtomicTransactionRunner(
    private val failOnBlock: suspend () -> Boolean,
) : AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
        if (failOnBlock()) throw InjectedTransactionFailure()
        return block()
    }
}

private object InjectedTransactionFailure : RuntimeException("injected transaction failure")
```

Also add `InMemoryWorkspaceFileBlobRepository` stub for `markReadyForGC` if not already present —
check `InMemoryWorkspaceFileBlobRepository` (line 620) has `markReadyForGC` implemented.

**Verify:** `just backend-test-fast` compiles the test file without errors.

---

### TASK-3.2: Add unit tests for DeleteWorkspaceAssetHandler transaction wrapping

**Type:** unit-test ✅ COMPLETE
**Files:**
`server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaCasHandlersTest.kt`

Add test helper for `DeleteWorkspaceAssetHandler` after line 781:

```kotlin
private fun deleteWorkspaceHandler(
    media: InMemoryMediaAssetRepository,
    blobs: InMemoryWorkspaceFileBlobRepository,
) = DeleteWorkspaceAssetHandler(
    media,
    FakeStorageService(),
    MediaUploadSettings(1, 200, "bucket"),
    NoopAtomicTransactionRunner,
    blobs,
)
```

Add these test functions:

1. **`DeleteWorkspaceAssetHandler runAtomically called on softDelete success`** — uses
   `RecordingAtomicTransactionRunner`; asserts `calls.size == 1` after successful delete.
2. **`DeleteWorkspaceAssetHandler marks blob READY_FOR_GC when softDelete fails`** — uses
   `FailingAtomicTransactionRunner` returning true on first call; asserts
   `blobs.blobs.values.any { it.status == BlobStatus.READY_FOR_GC }`.
3. **`DeleteWorkspaceAssetHandler storage delete failure propagates and softDelete is NOT called`
   ** — mock `FakeStorageService` to throw; use `RecordingAtomicTransactionRunner`; assert
   `calls.isEmpty()`.

**Verify:** `just backend-test-fast -- --tests "MediaCasHandlersTest"` passes.

---

### TASK-3.3: Add unit tests for UploadAssetHandler transaction wrapping

**Type:** unit-test ✅ COMPLETE
**Files:**
`server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaCasHandlersTest.kt`

Update `uploadHandler` helper to pass `transactionRunner` to `UploadAssetHandler` (currently it
creates `CasUploadAssetHandler` — create a separate helper for `UploadAssetHandler`).

Add test helper:

```kotlin
private fun uploadLegacyHandler(
    media: InMemoryMediaAssetRepository,
    blobs: InMemoryWorkspaceFileBlobRepository,
    storage: FakeStorage,
    emailStatus: EmailStatus = EmailStatus.VERIFIED,
    transactionRunner: AtomicTransactionRunner = NoopAtomicTransactionRunner,
) = UploadAssetHandler(
    media,
    InMemoryRateLimitRepository(),
    storage.service(),
    MediaUploadSettings(1, 200, "bucket"),
    FixedPrincipalContextProvider,
    FixedPrincipalIdentityLookup(emailStatus),
    emailVerificationPolicyOf(),
    transactionRunner,
)
```

Add tests:

1. **`UploadAssetHandler runAtomically called with markAsReady + releaseSlot block`** — use
   `RecordingAtomicTransactionRunner`; assert `calls.size == 1`.
2. **`UploadAssetHandler transaction rollback triggers handleUploadFailure`** — use
   `FailingAtomicTransactionRunner` that fails only the inner block; assert asset stays `UPLOADING`.
3. **`UploadAssetHandler happy path marks asset READY and releases slot`** — use
   `NoopAtomicTransactionRunner`; assert `asset.status == MediaAssetStatus.READY`.

**Verify:** `just backend-test-fast -- --tests "MediaCasHandlersTest"` passes.

---

### TASK-3.4: Add unit tests for PutAssetHandler handleNewBlob transaction wrapping

**Type:** unit-test ✅ COMPLETE
**Files:**
`server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaCasHandlersTest.kt`

**Note:** Test 2 (`createPendingAsset failure rolls back blob upsert`) was corrected. The
`upsertBlob` call in `PutAssetHandler.handle` (line 781) is OUTSIDE the `handleNewBlob`
transaction — it runs before `handleNewBlob` is called. Therefore when `createPendingAsset` fails
inside `runAtomically`, the blob created by the pre-transaction `upsertBlob` is NOT rolled back.
Test was renamed to `handleNewBlob createPendingAsset failure leaves blob in repository` and
assertion changed from `assertNull` to `assertNotNull`. The production code behavior is correct as
designed.

Add tests:

1. **`handleNewBlob calls runAtomically once with createPendingAsset`** — use
   `RecordingAtomicTransactionRunner` on `PutAssetHandler`; assert `calls.size == 1`; verify blob
   upsert happened.
2. **`handleNewBlob createPendingAsset failure rolls back blob upsert`** — inject
   `FailingAtomicTransactionRunner`; assert blob not persisted in `blobs`.

**Verify:** `just backend-test-fast -- --tests "MediaCasHandlersTest"` passes.

---

## Phase 4: Integration Tests

### TASK-4.1: Create MediaHandlersTransactionPostgresIntegrationTest

**Type:** integration-test
**Files:**
`server/smp/src/test/kotlin/com/profiletailors/smp/media/integration/MediaHandlersTransactionPostgresIntegrationTest.kt`

Create new file. Follow the pattern of `PublishingHandlersTransactionPostgresIntegrationTest.kt`:

- Use `@Tag("postgres")` and `@Testcontainers`
- Use `PostgresTestListener` for lifecycle
- Use real `R2dbcAtomicTransactionRunner` against a Testcontainers Postgres

Add the following test cases:

**`DeleteWorkspaceAssetHandler — storage delete succeeds + softDelete succeeds`**

- Seed asset with `READY` status and `storageKey`
- Mock storage delete to succeed
- Execute handler
- Assert asset status is `DELETED` in DB

**`DeleteWorkspaceAssetHandler — storage delete succeeds + softDelete fails → cleanup scheduled`**

- Seed asset with `READY` status
- Mock `softDelete` to throw via injected failure wrapper
- Execute handler
- Assert blob status is `READY_FOR_GC` (not orphaned) and `orphanedAt` is set
- Assert response `deleted == true`

**`UploadAssetHandler — markAsReady + releaseSlot both commit atomically`**

- Seed asset in `UPLOADING` status with valid slot
- Execute handler with valid upload bytes
- Assert asset status is `READY` and slot is released

**`UploadAssetHandler — releaseSlot fails → transaction rolls back`**

- Seed asset in `UPLOADING` status
- Inject failure into `releaseConcurrentUploadSlot` via wrapper
- Execute handler
- Assert asset status is still `UPLOADING` (rolled back)

**`PutAssetHandler handleNewBlob — upsertBlob + createPendingAsset both commit atomically`**

- Seed no blob for `(workspaceId, fileHash)`
- Execute handler
- Assert blob row exists with status `UPLOADING` and asset row with status `PENDING_UPLOAD`

**`PutAssetHandler handleNewBlob — createPendingAsset fails → blob upsert rolled back`**

- Seed no blob for `(workspaceId, fileHash)`
- Inject failure into `mediaAssetRepository.create` via wrapper after blob upsert succeeds
- Execute handler
- Assert no blob row exists (upsert rolled back)

**Verify:** `just backend-bdd-postgres` passes all scenarios.

---

## Implementation Order

1. **TASK-1.1 + TASK-1.2** ✅ — DI injection first (compilation gate for everything else)
2. **TASK-2.1** ✅ — DeleteWorkspaceAssetHandler transaction wrapping
3. **TASK-2.2** ✅ — UploadAssetHandler transaction wrapping
4. **TASK-2.3** ✅ — PutAssetHandler handleNewBlob wrapping
5. **TASK-3.1** ✅ — Add test doubles
6. **TASK-3.2** ✅ — DeleteWorkspaceAssetHandler unit tests
7. **TASK-3.3** ✅ — UploadAssetHandler unit tests
8. **TASK-3.4** ✅ — PutAssetHandler unit tests (with test correction — see note)
9. **TASK-4.1** 🔲 — Postgres integration tests (pending)

**No parallel/conflicting tasks.** Each task depends on the previous phase compiling before the next
begins.
