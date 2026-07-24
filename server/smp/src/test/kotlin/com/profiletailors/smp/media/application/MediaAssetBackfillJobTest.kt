package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.media.domain.MediaStorageKeys
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.Storage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant

class MediaAssetBackfillJobTest {

    @Test
    fun `run backfills assets without hash and updates metadata`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES
        val hash = sha256(bytes)
        val asset = readyAssetWithoutHash("asset-1", storageKey = "assets/ws-test/temp/asset-1.jpg")
        media.create(asset)
        storage.upload("bucket", "assets/ws-test/temp/asset-1.jpg", flowOf(bytes), emptyMap())

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 10)

        assertEquals(1, result.scanned)
        assertEquals(1, result.backfilled)
        assertEquals(0, result.failed)

        val updated = media.asset(WORKSPACE, "asset-1")
        assertNotNull(updated)
        assertEquals(hash, updated?.fileHash)
        assertEquals(MediaStorageKeys.canonicalKey(WORKSPACE, hash, "image/jpeg"), updated?.storageKey)
        assertEquals(bytes.size.toLong(), updated?.fileSizeBytes)
    }

    @Test
    fun `run copies to canonical key when storage key differs`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES
        val hash = sha256(bytes)
        val srcKey = "assets/ws-test/temp/asset-1.jpg"
        val asset = readyAssetWithoutHash("asset-1", storageKey = srcKey)
        media.create(asset)
        storage.upload("bucket", srcKey, flowOf(bytes), emptyMap())

        val job = backfillJob(media, blobs, storage)

        job.run(limit = 10)

        val canonicalKey = MediaStorageKeys.canonicalKey(WORKSPACE, hash, "image/jpeg")
        assertTrue(storage.uploadedKeys.contains(canonicalKey), "Storage must contain the canonical key after copy")
    }

    @Test
    fun `run does not copy when asset already at canonical key`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES
        val hash = sha256(bytes)
        val canonicalKey = MediaStorageKeys.canonicalKey(WORKSPACE, hash, "image/jpeg")
        val asset = readyAssetWithoutHash("asset-1", storageKey = canonicalKey)
        media.create(asset)
        storage.upload("bucket", canonicalKey, flowOf(bytes), emptyMap())

        val job = backfillJob(media, blobs, storage)

        job.run(limit = 10)

        // Only the canonical key should be present, no extra copies
        assertEquals(1, storage.uploadedKeys.size, "No extra copies should be made when already at canonical key")
        assertEquals(canonicalKey, storage.uploadedKeys.first())
    }

    @Test
    fun `run skips assets with blank storage key`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        // Use reflection to bypass domain validation (simulates legacy DB row)
        val asset = readyAssetWithoutHash("asset-1", storageKey = "dummy")
            .unsafeWithStorageKey("")
        media.create(asset)

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 10)

        assertEquals(1, result.scanned)
        assertEquals(0, result.backfilled)
        assertEquals(1, result.failed)
    }

    @Test
    fun `run continues after individual asset failure`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES

        // First asset has blank storage key → will fail (simulates legacy DB row)
        val asset1 = readyAssetWithoutHash("asset-1", storageKey = "dummy")
            .unsafeWithStorageKey("")
        media.create(asset1)

        // Second asset is valid → will succeed
        val asset2 = readyAssetWithoutHash("asset-2", storageKey = "assets/ws-test/temp/asset-2.jpg")
        media.create(asset2)
        storage.upload("bucket", "assets/ws-test/temp/asset-2.jpg", flowOf(bytes), emptyMap())

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 10)

        assertEquals(2, result.scanned)
        assertEquals(1, result.backfilled)
        assertEquals(1, result.failed)

        val updated2 = media.asset(WORKSPACE, "asset-2")
        assertNotNull(updated2)
        assertEquals(sha256(bytes), updated2?.fileHash)
    }

    @Test
    fun `run handles storage download failure gracefully`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val asset = readyAssetWithoutHash("asset-1", storageKey = "assets/ws-test/temp/asset-1.jpg")
        media.create(asset)
        // Do NOT upload the object to storage → download will throw

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 10)

        assertEquals(1, result.scanned)
        assertEquals(0, result.backfilled)
        assertEquals(1, result.failed)
    }

    @Test
    fun `run respects batch limit`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES

        (1..5).forEach { i ->
            val asset = readyAssetWithoutHash("asset-$i", storageKey = "assets/ws-test/temp/asset-$i.jpg")
            media.create(asset)
            storage.upload("bucket", "assets/ws-test/temp/asset-$i.jpg", flowOf(bytes), emptyMap())
        }

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 3)

        assertEquals(3, result.scanned)
        assertEquals(3, result.backfilled)
        assertEquals(0, result.failed)
    }

    @Test
    fun `run reports zero when no candidates`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 10)

        assertEquals(0, result.scanned)
        assertEquals(0, result.backfilled)
        assertEquals(0, result.failed)
    }

    @Test
    fun `run updates blob ready and asset cas metadata`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES
        val hash = sha256(bytes)
        val asset = readyAssetWithoutHash("asset-1", storageKey = "assets/ws-test/temp/asset-1.jpg")
        media.create(asset)
        storage.upload("bucket", "assets/ws-test/temp/asset-1.jpg", flowOf(bytes), emptyMap())

        val job = backfillJob(media, blobs, storage)

        job.run(limit = 10)

        val canonicalKey = MediaStorageKeys.canonicalKey(WORKSPACE, hash, "image/jpeg")
        val blob = blobs.getBlob(WORKSPACE, hash)
        assertNotNull(blob, "Blob should be upserted")
        assertEquals(canonicalKey, blob?.storageKey)
        assertEquals(bytes.size.toLong(), blob?.fileSizeBytes)
    }

    @Test
    fun `run updates asset with detected media type from detectedMediaType field`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES
        val hash = sha256(bytes)
        // Asset has detectedMediaType set (as happens with reconciled legacy assets)
        val asset = MediaAsset(
            assetId = "asset-1",
            workspaceId = WORKSPACE,
            sourceType = MediaSourceType.UPLOADED,
            fileHash = null,
            mediaType = "image/jpeg",
            storageKey = "assets/ws-test/temp/asset-1.jpg",
            detectedMediaType = "image/jpeg",
            originalFilename = "photo.jpg",
            fileSizeBytes = null,
            status = MediaAssetStatus.READY,
            createdAt = Instant.now(),
        )
        media.create(asset)
        storage.upload("bucket", "assets/ws-test/temp/asset-1.jpg", flowOf(bytes), emptyMap())

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 10)

        assertEquals(1, result.backfilled)
        val updated = media.asset(WORKSPACE, "asset-1")
        assertEquals("image/jpeg", updated?.detectedMediaType)
        assertEquals(bytes.size.toLong(), updated?.fileSizeBytes)
    }

    @Test
    fun `run upserts blob before marking ready`() = runTest {
        val media = BackfillInMemoryMediaAssetRepository()
        val blobs = BackfillInMemoryWorkspaceFileBlobRepository()
        val storage = BackfillInMemoryFakeStorage()
        val bytes = JPEG_BYTES
        val hash = sha256(bytes)
        val asset = readyAssetWithoutHash("asset-1", storageKey = "assets/ws-test/temp/asset-1.jpg")
        media.create(asset)
        storage.upload("bucket", "assets/ws-test/temp/asset-1.jpg", flowOf(bytes), emptyMap())

        // Pre-insert the blob in UPLOADING status to test upsert returns Existed
        blobs.saveBlob(backfillUploadingBlob(hash))

        val job = backfillJob(media, blobs, storage)

        val result = job.run(limit = 10)

        assertEquals(1, result.backfilled)
        val blob = blobs.getBlob(WORKSPACE, hash)
        assertNotNull(blob)
        assertEquals(
            com.profiletailors.smp.media.domain.BlobStatus.READY,
            blob?.status,
            "Existing uploading blob should be marked READY after backfill",
        )
    }

    /**
     * Creates a copy of a [MediaAsset] with the given [storageKey], bypassing
     * domain constructor validation. Used to simulate legacy DB rows (e.g.
     * READY + blank storageKey) that cannot be created through the constructor.
     */
    private fun MediaAsset.unsafeWithStorageKey(storageKey: String?): MediaAsset {
        // Identity copy — passes constructor validation because current storageKey is valid
        val copy = copy(storageKey = this.storageKey)
        // Force the field via Java reflection to bypass domain validation
        val field = MediaAsset::class.java.getDeclaredField("storageKey")
        field.isAccessible = true
        field.set(copy, storageKey)
        return copy
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun backfillJob(
        media: BackfillInMemoryMediaAssetRepository,
        blobs: BackfillInMemoryWorkspaceFileBlobRepository,
        storage: BackfillInMemoryFakeStorage,
    ): MediaAssetBackfillJob {
        val storageService = StorageApplicationService(
            storage,
            BackfillNoopEventPublisher(),
            BackfillNoopStorageObservation(),
        )
        return MediaAssetBackfillJob(
            mediaAssetRepository = media,
            workspaceFileBlobRepository = blobs,
            storageApplicationService = storageService,
            uploadSettings = MediaUploadSettings(5, 200, "bucket"),
            transactionRunner = BackfillNoopAtomicTransactionRunner,
        )
    }

    private fun readyAssetWithoutHash(assetId: String, storageKey: String, mediaType: String = "image/jpeg") =
        MediaAsset(
            assetId = assetId,
            workspaceId = WORKSPACE,
            sourceType = MediaSourceType.UPLOADED,
            fileHash = null,
            mediaType = mediaType,
            storageKey = storageKey,
            detectedMediaType = if (mediaType == "image/png") "image/png" else "image/jpeg",
            originalFilename = "photo.jpg",
            fileSizeBytes = null,
            status = MediaAssetStatus.READY,
            createdAt = Instant.now(),
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val WORKSPACE = "ws-test"
        private val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02)
    }
}

// ---------------------------------------------------------------------------
// Minimal in-memory fakes scoped to this test file (prefixed to avoid
// clashing with identically-named private helpers in MediaCasHandlersTest.kt).
// ---------------------------------------------------------------------------

/**
 * In-memory [MediaAssetRepository] that only implements the methods
 * needed by [MediaAssetBackfillJob].
 */
private class BackfillInMemoryMediaAssetRepository : MediaAssetRepository {
    val assets = linkedMapOf<Pair<String, String>, MediaAsset>()

    fun asset(workspaceId: String, assetId: String) = assets[workspaceId to assetId]

    override suspend fun findReadyAssetsWithoutHash(limit: Int): List<MediaAsset> = assets.values
        .filter { it.status == MediaAssetStatus.READY && it.fileHash == null }
        .take(limit)

    override suspend fun updateReadyAssetCasMetadata(
        assetId: String,
        workspaceId: String,
        fileHash: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long,
    ): MediaAsset? {
        val key = workspaceId to assetId
        val existing = assets[key] ?: return null
        val updated = existing.copy(
            fileHash = fileHash,
            storageKey = storageKey,
            detectedMediaType = detectedMediaType,
            fileSizeBytes = fileSizeBytes,
        )
        assets[key] = updated
        return updated
    }

    override suspend fun create(asset: MediaAsset): MediaAsset {
        assets[asset.workspaceId to asset.assetId] = asset
        return asset
    }

    override suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String) = asset(workspaceId, assetId)

    // Everything else returns empty/null
    override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>) = emptyList<MediaAsset>()
    override suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<MediaAssetStatus>,
        pageSize: Int,
        cursor: String?,
    ) = PagedMediaAssets(emptyList(), null)
    override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant) = false
    override suspend fun claimCasUploadSlot(assetId: String, workspaceId: String, now: Instant) = false
    override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long) = null
    override suspend fun markAsReadyFromDedup(
        assetId: String,
        workspaceId: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long?,
    ) = null
    override suspend fun markAsFailed(assetId: String, workspaceId: String, reason: String?) = null
    override suspend fun updateStatus(assetId: String, workspaceId: String, status: MediaAssetStatus) = null
    override suspend fun softDelete(assetId: String, workspaceId: String) = null
    override suspend fun findStaleProcessingAssets(thresholdHours: Long, gracePeriodMinutes: Long) =
        emptyList<MediaAsset>()
    override suspend fun findRecentlyFailedAssets() = emptyList<MediaAsset>()
    override suspend fun findExpiredPendingUploadAssets(limit: Int) = emptyList<MediaAsset>()
    override suspend fun findExpiredUploadingAssets(limit: Int) = emptyList<MediaAsset>()
    override suspend fun countActiveReferences(workspaceId: String, fileHash: String) = 0
    override suspend fun findActiveByWorkspaceAndHash(workspaceId: String, fileHash: String) = null
}

/**
 * In-memory [WorkspaceFileBlobRepository] that only implements the methods
 * needed by [MediaAssetBackfillJob].
 */
private class BackfillInMemoryWorkspaceFileBlobRepository : WorkspaceFileBlobRepository {
    val blobs = linkedMapOf<Pair<String, String>, com.profiletailors.smp.media.domain.WorkspaceFileBlob>()

    fun getBlob(workspaceId: String, fileHash: String) = blobs[workspaceId to fileHash]

    override suspend fun findByWorkspaceAndHash(workspaceId: String, fileHash: String) = getBlob(workspaceId, fileHash)

    fun saveBlob(blob: com.profiletailors.smp.media.domain.WorkspaceFileBlob) {
        blobs[blob.workspaceId to blob.fileHash] = blob
    }

    override suspend fun upsertBlob(
        workspaceId: String,
        fileHash: String,
    ): com.profiletailors.smp.media.domain.BlobUpsertResult {
        val existing = getBlob(workspaceId, fileHash)
        if (existing != null) return com.profiletailors.smp.media.domain.BlobUpsertResult.Existed(existing)
        val created = com.profiletailors.smp.media.domain.WorkspaceFileBlob(
            workspaceId = workspaceId,
            fileHash = fileHash,
            storageKey = null,
            fileSizeBytes = null,
            detectedMediaType = null,
            status = com.profiletailors.smp.media.domain.BlobStatus.UPLOADING,
            createdAt = Instant.now(),
        )
        saveBlob(created)
        return com.profiletailors.smp.media.domain.BlobUpsertResult.Created(created)
    }

    override suspend fun markBlobReady(
        workspaceId: String,
        fileHash: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long,
    ) {
        val key = workspaceId to fileHash
        val existing = blobs[key] ?: return
        blobs[key] = existing.copy(
            status = com.profiletailors.smp.media.domain.BlobStatus.READY,
            storageKey = storageKey,
            detectedMediaType = detectedMediaType,
            fileSizeBytes = fileSizeBytes,
        )
    }

    // Everything else throws or returns empty
    override suspend fun findBlobForUpdate(workspaceId: String, fileHash: String) = getBlob(workspaceId, fileHash)
    override suspend fun countActiveReferences(workspaceId: String, fileHash: String) = 0
    override suspend fun markReadyForGC(workspaceId: String, fileHash: String, orphanedAt: Instant) = Unit
    override suspend fun markAsGarbageCollected(workspaceId: String, fileHash: String) = Unit
    override suspend fun findReadyForGC(
        threshold: Instant,
        batchSize: Int,
    ): kotlinx.coroutines.flow.Flow<com.profiletailors.smp.media.domain.WorkspaceFileBlob> =
        kotlinx.coroutines.flow.emptyFlow()
    override suspend fun recordGCFailure(workspaceId: String, fileHash: String, failureReason: String) = Unit
    override suspend fun resetBlobToUploading(workspaceId: String, fileHash: String) = Unit
    override suspend fun markBlobFailed(workspaceId: String, fileHash: String, failureReason: String) = Unit
}

/**
 * In-memory [Storage] that tracks uploaded keys.
 */
private class BackfillInMemoryFakeStorage : Storage {
    val uploadedKeys = mutableListOf<String>()
    val uploadedMetadata = mutableMapOf<String, Map<String, String>>()
    private val objects = mutableMapOf<String, ByteArray>()

    override suspend fun upload(
        bucket: String,
        key: String,
        content: kotlinx.coroutines.flow.Flow<ByteArray>,
        metadata: Map<String, String>,
    ) {
        uploadedKeys += key
        uploadedMetadata[key] = metadata
        val chunks = mutableListOf<ByteArray>()
        content.collect { chunks += it }
        objects["$bucket/$key"] = chunks.flatMap { it.toList() }.toByteArray()
    }

    override fun download(bucket: String, key: String): kotlinx.coroutines.flow.Flow<ByteArray> {
        val data = objects["$bucket/$key"]
            ?: throw com.profiletailors.storage.domain.StorageObjectNotFoundException(bucket, key)
        return flowOf(data)
    }

    override suspend fun delete(bucket: String, key: String) {
        objects.remove("$bucket/$key")
    }

    override suspend fun list(bucket: String, prefix: String) = emptyList<String>()
    override suspend fun exists(bucket: String, key: String) = objects.containsKey("$bucket/$key")

    override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
        val sourcePath = "$bucket/$sourceKey"
        val data = objects[sourcePath]
            ?: throw com.profiletailors.storage.domain.StorageServiceException("copy source not found: $sourceKey")
        objects["$bucket/$destKey"] = data
        uploadedKeys += destKey
    }
}

private class BackfillNoopEventPublisher :
    com.profiletailors.common.domain.bus.event.EventPublisher<
        com.profiletailors.common.domain.bus.event.BaseDomainEvent,
        > {
    override suspend fun publish(event: com.profiletailors.common.domain.bus.event.BaseDomainEvent) = Unit
    override suspend fun publish(events: List<com.profiletailors.common.domain.bus.event.BaseDomainEvent>) = Unit
}

private class BackfillNoopStorageObservation : com.profiletailors.storage.domain.StorageObservation {
    override fun recordOperation(operation: String, provider: String, bucket: String, success: Boolean) = Unit
    override fun recordBytesUploaded(bytes: Long, provider: String, bucket: String) = Unit
    override fun recordBytesDownloaded(bytes: Long, provider: String, bucket: String) = Unit
    override fun recordOperationLatency(operation: String, provider: String, durationNanos: Long) = Unit
    override fun recordError(operation: String, provider: String, bucket: String, errorType: String) = Unit
    override fun recordPresignedUrlGenerated(provider: String, success: Boolean) = Unit
    override suspend fun <T : Any> recordOperationTime(
        operation: String,
        provider: String,
        action: suspend () -> T,
    ): T = action()
}

private object BackfillNoopAtomicTransactionRunner : AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
}

private fun backfillUploadingBlob(hash: String, workspaceId: String = "ws-test") =
    com.profiletailors.smp.media.domain.WorkspaceFileBlob(
        workspaceId = workspaceId,
        fileHash = hash,
        storageKey = null,
        fileSizeBytes = null,
        detectedMediaType = null,
        status = com.profiletailors.smp.media.domain.BlobStatus.UPLOADING,
        createdAt = Instant.now(),
    )
