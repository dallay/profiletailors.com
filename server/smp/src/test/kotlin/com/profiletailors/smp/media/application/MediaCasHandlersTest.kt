package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.domain.BlobStatus
import com.profiletailors.smp.media.domain.BlobUpsertResult
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.media.domain.MediaStorageKeys
import com.profiletailors.smp.media.domain.WorkspaceFileBlob
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.domain.StorageObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest
import java.time.Instant

class MediaCasHandlersTest {
    @Test
    fun `PUT new asset creates uploading blob and pending asset with declared file size`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val handler = putHandler(media, blobs)

        val result = handler.handle(putCommand(assetId = ASSET_A, fileHash = HASH_A, fileSizeBytes = 4))

        assertTrue(result is PutAssetResult.Created)
        assertEquals(BlobStatus.UPLOADING, blobs.blob(WORKSPACE, HASH_A)?.status)
        val asset = media.asset(WORKSPACE, ASSET_A)
        assertEquals(MediaAssetStatus.PENDING_UPLOAD, asset?.status)
        assertEquals(4L, asset?.fileSizeBytes)
        assertNull(asset?.storageKey)
    }

    @Test
    fun `PUT with READY blob creates ready deduped asset without upload`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        blobs.saveBlob(readyBlob(HASH_A))
        val handler = putHandler(media, blobs)

        val result = handler.handle(putCommand(assetId = ASSET_A, fileHash = HASH_A))

        assertTrue(result is PutAssetResult.AlreadyExists)
        val response = result as PutAssetResult.AlreadyExists
        assertTrue(response.deduped)
        assertEquals(MediaAssetStatus.READY, media.asset(WORKSPACE, ASSET_A)?.status)
        assertEquals("assets/$WORKSPACE/blobs/$HASH_A.jpg", media.asset(WORKSPACE, ASSET_A)?.storageKey)
    }

    @Test
    fun `PUT with UPLOADING blob returns waiting for blob`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        blobs.saveBlob(uploadingBlob(HASH_A))

        val result = putHandler(media, blobs).handle(putCommand(assetId = ASSET_A, fileHash = HASH_A))

        assertTrue(result is PutAssetResult.WaitingForBlob)
        assertEquals(3, (result as PutAssetResult.WaitingForBlob).retryAfterSeconds)
        assertNull(media.asset(WORKSPACE, ASSET_A))
    }

    @Test
    fun `PUT with FAILED blob resets blob and creates pending upload`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        blobs.saveBlob(uploadingBlob(HASH_A).copy(status = BlobStatus.FAILED, failureReason = "HASH_MISMATCH"))

        val result = putHandler(media, blobs).handle(putCommand(assetId = ASSET_A, fileHash = HASH_A))

        assertTrue(result is PutAssetResult.Created)
        assertEquals(BlobStatus.UPLOADING, blobs.blob(WORKSPACE, HASH_A)?.status)
        assertNull(blobs.blob(WORKSPACE, HASH_A)?.failureReason)
        assertEquals(MediaAssetStatus.PENDING_UPLOAD, media.asset(WORKSPACE, ASSET_A)?.status)
    }

    @Test
    fun `PUT idempotent same asset id and hash returns current state`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        media.create(pendingAsset(ASSET_A, HASH_A))

        val result = putHandler(media, blobs).handle(putCommand(assetId = ASSET_A, fileHash = HASH_A))

        assertTrue(result is PutAssetResult.AlreadyExists)
        assertEquals(MediaAssetStatus.PENDING_UPLOAD.name, (result as PutAssetResult.AlreadyExists).status)
    }

    @Test
    fun `PUT same asset id with different hash returns hash mismatch`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        media.create(pendingAsset(ASSET_A, HASH_A))

        val result = putHandler(media, blobs).handle(putCommand(assetId = ASSET_A, fileHash = HASH_B))

        assertTrue(result is PutAssetResult.HashMismatch)
        assertEquals(HASH_A, (result as PutAssetResult.HashMismatch).existingFileHash)
    }

    @Test
    fun `PUT rate limit is checked before creating asset`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val limiter = InMemoryRateLimitRepository(allowCreates = false)

        assertThrows<RateLimitExceededException> {
            putHandler(media, blobs, limiter).handle(putCommand(assetId = ASSET_A, fileHash = HASH_A))
        }

        // No asset is created because the rate limit check fires inside createPendingAsset
        assertEquals(0, media.assets.size)
        // A blob row IS created by upsertBlob (INSERT ON CONFLICT DO NOTHING) before
        // the rate limit check — this is correct: the blob exists for the next caller
        assertEquals(1, blobs.blobs.size)
    }

    @Test
    fun `successful upload verifies size and hash then copies temp to canonical detected key`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val storage = FakeStorage()
        val bytes = jpegBytes()
        media.create(pendingAsset(ASSET_A, sha256(bytes), fileSizeBytes = bytes.size.toLong()))
        blobs.saveBlob(uploadingBlob(sha256(bytes)))

        val result = uploadHandler(media, blobs, storage).handle(uploadCommand(ASSET_A, sha256(bytes), bytes))

        assertTrue(result is CasUploadAssetResult.Ready)
        val ready = result as CasUploadAssetResult.Ready
        assertFalse(ready.deduped)
        val canonicalKey = "assets/$WORKSPACE/blobs/${sha256(bytes)}.jpg"
        assertEquals(canonicalKey, media.asset(WORKSPACE, ASSET_A)?.storageKey)
        assertEquals(BlobStatus.READY, blobs.blob(WORKSPACE, sha256(bytes))?.status)
        assertEquals(canonicalKey, blobs.blob(WORKSPACE, sha256(bytes))?.storageKey)
        assertTrue(storage.deletedKeys.contains("assets/$WORKSPACE/temp/$ASSET_A.jpg"))
    }

    @Test
    fun `upload dedup when concurrent upload completed first updates asset to ready`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val storage = FakeStorage()
        val bytes = jpegBytes()
        val hash = sha256(bytes)
        media.create(
            pendingAsset(ASSET_A, hash, fileSizeBytes = bytes.size.toLong(), status = MediaAssetStatus.UPLOADING),
        )
        blobs.saveBlob(readyBlob(hash, fileSizeBytes = bytes.size.toLong()))

        val result = uploadHandler(media, blobs, storage).handle(uploadCommand(ASSET_A, hash, bytes))

        assertTrue(result is CasUploadAssetResult.Ready)
        assertTrue((result as CasUploadAssetResult.Ready).deduped)
        assertEquals(MediaAssetStatus.READY, media.asset(WORKSPACE, ASSET_A)?.status)
        assertTrue(storage.deletedKeys.contains("assets/$WORKSPACE/temp/$ASSET_A.jpg"))
    }

    @Test
    fun `upload hash mismatch marks blob and asset failed and deletes temp`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val storage = FakeStorage()
        val bytes = jpegBytes()
        media.create(pendingAsset(ASSET_A, HASH_A, fileSizeBytes = bytes.size.toLong()))
        blobs.saveBlob(uploadingBlob(HASH_A))

        assertThrows<UploadHashMismatchException> {
            uploadHandler(media, blobs, storage).handle(uploadCommand(ASSET_A, HASH_A, bytes))
        }

        assertEquals(MediaAssetStatus.FAILED, media.asset(WORKSPACE, ASSET_A)?.status)
        assertEquals("HASH_MISMATCH", media.asset(WORKSPACE, ASSET_A)?.failureReason)
        assertEquals(BlobStatus.FAILED, blobs.blob(WORKSPACE, HASH_A)?.status)
        assertEquals("HASH_MISMATCH", media.asset(WORKSPACE, ASSET_A)?.failureReason)
        assertTrue(storage.deletedKeys.contains("assets/$WORKSPACE/temp/$ASSET_A.jpg"))
    }

    @Test
    fun `upload file size mismatch marks blob and asset failed and deletes temp`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val storage = FakeStorage()
        val bytes = jpegBytes()
        val hash = sha256(bytes)
        media.create(pendingAsset(ASSET_A, hash, fileSizeBytes = bytes.size.toLong() + 1))
        blobs.saveBlob(uploadingBlob(hash))

        assertThrows<UploadFileSizeMismatchException> {
            uploadHandler(media, blobs, storage).handle(
                uploadCommand(ASSET_A, hash, bytes, declaredSize = bytes.size.toLong() + 1),
            )
        }

        assertEquals(MediaAssetStatus.FAILED, media.asset(WORKSPACE, ASSET_A)?.status)
        assertEquals("FILE_SIZE_MISMATCH", blobs.blob(WORKSPACE, hash)?.failureReason)
    }

    @Test
    fun `declared MIME differs from detected MIME uses declared temp extension and detected canonical extension`() =
        runTest {
            val media = InMemoryMediaAssetRepository()
            val blobs = InMemoryWorkspaceFileBlobRepository()
            val storage = FakeStorage()
            val bytes = jpegBytes()
            val hash = sha256(bytes)
            media.create(pendingAsset(ASSET_A, hash, mediaType = "image/png", fileSizeBytes = bytes.size.toLong()))
            blobs.saveBlob(uploadingBlob(hash))

            uploadHandler(media, blobs, storage).handle(uploadCommand(ASSET_A, hash, bytes, declaredType = "image/png"))

            assertEquals(
                listOf("assets/$WORKSPACE/temp/$ASSET_A.png" to "assets/$WORKSPACE/blobs/$hash.jpg"),
                storage.copies,
            )
        }

    @Test
    fun `DELETE last active asset marks blob ready for gc`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        media.create(readyAsset(ASSET_A, HASH_A))
        blobs.saveBlob(readyBlob(HASH_A))

        val result = deleteHandler(media, blobs).handle(DeleteAssetCommand(ASSET_A, WORKSPACE))

        assertTrue(result.blobScheduledForGC)
        assertEquals(MediaAssetStatus.DELETED, media.asset(WORKSPACE, ASSET_A)?.status)
        assertEquals(BlobStatus.READY_FOR_GC, blobs.blob(WORKSPACE, HASH_A)?.status)
        assertNotNull(blobs.blob(WORKSPACE, HASH_A)?.orphanedAt)
    }

    @Test
    fun `DELETE asset with other active references keeps blob ready`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        media.create(readyAsset(ASSET_A, HASH_A))
        media.create(readyAsset(ASSET_B, HASH_A))
        blobs.saveBlob(readyBlob(HASH_A))

        val result = deleteHandler(media, blobs).handle(DeleteAssetCommand(ASSET_A, WORKSPACE))

        assertFalse(result.blobScheduledForGC)
        assertEquals(BlobStatus.READY, blobs.blob(WORKSPACE, HASH_A)?.status)
    }

    @Test
    fun `DELETE already deleted is idempotent and does not reschedule gc`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        media.create(readyAsset(ASSET_A, HASH_A).copy(status = MediaAssetStatus.DELETED))
        blobs.saveBlob(readyBlob(HASH_A))

        val result = deleteHandler(media, blobs).handle(DeleteAssetCommand(ASSET_A, WORKSPACE))

        assertTrue(result.deleted)
        assertFalse(result.blobScheduledForGC)
        assertEquals(BlobStatus.READY, blobs.blob(WORKSPACE, HASH_A)?.status)
    }

    @Test
    fun `GC deletes storage and preserves blob row as garbage collected`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val storage = FakeStorage()
        blobs.saveBlob(
            readyBlob(HASH_A).copy(
                status = BlobStatus.READY_FOR_GC,
                orphanedAt = Instant.now().minusSeconds(8 * 24 * 3600),
            ),
        )

        val result = BlobGarbageCollector(blobs, media, storage.service(), reconcilerSettings()).run()

        assertEquals(1, result.blobsDeleted)
        assertEquals(BlobStatus.GARBAGE_COLLECTED, blobs.blob(WORKSPACE, HASH_A)?.status)
        assertTrue(storage.deletedKeys.contains("assets/$WORKSPACE/blobs/$HASH_A.jpg"))
    }

    @Test
    fun `GC skips blobs with too many failures`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val storage = FakeStorage()
        blobs.saveBlob(
            readyBlob(HASH_A).copy(
                status = BlobStatus.READY_FOR_GC,
                orphanedAt = Instant.now().minusSeconds(8 * 24 * 3600),
                gcFailureCount = MediaAsset.GC_MAX_FAILURE_COUNT,
            ),
        )

        val result = BlobGarbageCollector(blobs, media, storage.service(), reconcilerSettings()).run()

        assertEquals(0, result.blobsScanned)
        assertEquals(BlobStatus.READY_FOR_GC, blobs.blob(WORKSPACE, HASH_A)?.status)
    }

    @Test
    fun `PENDING_UPLOAD expiration marks failed and schedules orphan blob for gc`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val limiter = InMemoryRateLimitRepository()
        media.create(pendingAsset(ASSET_A, HASH_A).copy(createdAt = Instant.now().minusSeconds(25 * 3600)))
        blobs.saveBlob(uploadingBlob(HASH_A))

        val result = MediaAssetExpirationJob(media, blobs, limiter, NoopAtomicTransactionRunner).run()

        assertEquals(1, result.pendingExpired)
        assertEquals("expired:pending_upload_ttl", media.asset(WORKSPACE, ASSET_A)?.failureReason)
        assertEquals(BlobStatus.READY_FOR_GC, blobs.blob(WORKSPACE, HASH_A)?.status)
    }

    @Test
    fun `UPLOADING expiration marks failed and only schedules blob gc when no active references remain`() = runTest {
        val media = InMemoryMediaAssetRepository()
        val blobs = InMemoryWorkspaceFileBlobRepository()
        val limiter = InMemoryRateLimitRepository()
        media.create(
            pendingAsset(ASSET_A, HASH_A, status = MediaAssetStatus.UPLOADING)
                .copy(uploadStartedAt = Instant.now().minusSeconds(25 * 3600)),
        )
        blobs.saveBlob(uploadingBlob(HASH_A))

        val result = MediaAssetExpirationJob(media, blobs, limiter, NoopAtomicTransactionRunner).run()

        assertEquals(1, result.uploadingExpired)
        assertEquals("expired:uploading_ttl", media.asset(WORKSPACE, ASSET_A)?.failureReason)
        assertEquals(BlobStatus.READY_FOR_GC, blobs.blob(WORKSPACE, HASH_A)?.status)
    }

    @Test
    fun `MediaStorageKeys generates canonical and temp keys from expected MIME source`() {
        assertEquals("assets/ws_abc/blobs/$HASH_A.jpg", MediaStorageKeys.canonicalKey("ws_abc", HASH_A, "image/jpeg"))
        assertEquals("assets/ws_abc/temp/$ASSET_A.png", MediaStorageKeys.tempKey("ws_abc", ASSET_A, "image/png"))
        assertEquals("assets/ws_abc/blobs/$HASH_A.jpg", MediaStorageKeys.canonicalKey("ws_abc", HASH_A, "image/jpeg"))
    }
}

private class InMemoryMediaAssetRepository : MediaAssetRepository {
    val assets = linkedMapOf<Pair<String, String>, MediaAsset>()

    fun asset(workspaceId: String, assetId: String) = assets[workspaceId to assetId]

    override suspend fun create(asset: MediaAsset): MediaAsset {
        assets[asset.workspaceId to asset.assetId] = asset
        return asset
    }

    override suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String): MediaAsset? =
        asset(workspaceId, assetId)
    override suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>) =
        assetIds.mapNotNull { asset(workspaceId, it) }
    override suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<MediaAssetStatus>,
        pageSize: Int,
        cursor: String?,
    ) = PagedMediaAssets(emptyList(), null)
    override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant) = false

    override suspend fun claimCasUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean {
        val asset = asset(workspaceId, assetId) ?: return false
        if (asset.status !in setOf(MediaAssetStatus.PENDING_UPLOAD, MediaAssetStatus.FAILED)) return false
        assets[workspaceId to assetId] = asset.copy(status = MediaAssetStatus.UPLOADING, uploadStartedAt = now)
        return true
    }

    override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset? {
        val asset = asset(workspaceId, assetId) ?: return null
        val updated = asset.copy(status = MediaAssetStatus.READY, fileSizeBytes = fileSizeBytes)
        assets[workspaceId to assetId] = updated
        return updated
    }

    override suspend fun markAsReadyFromDedup(
        assetId: String,
        workspaceId: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long?,
    ): MediaAsset? {
        val asset = asset(workspaceId, assetId) ?: return null
        val updated = asset.copy(
            status = MediaAssetStatus.READY,
            storageKey = storageKey,
            detectedMediaType = detectedMediaType,
            fileSizeBytes = fileSizeBytes ?: asset.fileSizeBytes,
        )
        assets[workspaceId to assetId] = updated
        return updated
    }

    override suspend fun markAsFailed(assetId: String, workspaceId: String, reason: String?): MediaAsset? {
        val asset = asset(workspaceId, assetId) ?: return null
        val updated = asset.copy(status = MediaAssetStatus.FAILED, failureReason = reason, uploadStartedAt = null)
        assets[workspaceId to assetId] = updated
        return updated
    }

    override suspend fun softDelete(assetId: String, workspaceId: String): MediaAsset? {
        val asset = asset(workspaceId, assetId) ?: return null
        val updated = asset.copy(status = MediaAssetStatus.DELETED)
        assets[workspaceId to assetId] = updated
        return updated
    }

    override suspend fun findStaleProcessingAssets(thresholdHours: Long, gracePeriodMinutes: Long) =
        emptyList<MediaAsset>()
    override suspend fun findRecentlyFailedAssets() = emptyList<MediaAsset>()
    override suspend fun findExpiredPendingUploadAssets(limit: Int) = assets.values.filter {
        it.status == MediaAssetStatus.PENDING_UPLOAD &&
            it.createdAt.isBefore(Instant.now().minusSeconds(24 * 3600))
    }.take(limit)
    override suspend fun findExpiredUploadingAssets(limit: Int) = assets.values.filter {
        it.status == MediaAssetStatus.UPLOADING &&
            it.uploadStartedAt?.isBefore(Instant.now().minusSeconds(24 * 3600)) == true
    }.take(limit)
    override suspend fun countActiveReferences(workspaceId: String, fileHash: String) = assets.values.count {
        it.workspaceId == workspaceId &&
            it.fileHash == fileHash &&
            it.status !in setOf(MediaAssetStatus.DELETED, MediaAssetStatus.FAILED)
    }
}

private class InMemoryWorkspaceFileBlobRepository : WorkspaceFileBlobRepository {
    val blobs = linkedMapOf<Pair<String, String>, WorkspaceFileBlob>()
    fun blob(workspaceId: String, fileHash: String) = blobs[workspaceId to fileHash]
    fun saveBlob(blob: WorkspaceFileBlob) {
        blobs[blob.workspaceId to blob.fileHash] = blob
    }

    override suspend fun upsertBlob(workspaceId: String, fileHash: String): BlobUpsertResult {
        val existing = blob(workspaceId, fileHash)
        if (existing != null) return BlobUpsertResult.Existed(existing)
        val created = uploadingBlob(fileHash, workspaceId)
        saveBlob(created)
        return BlobUpsertResult.Created(created)
    }

    override suspend fun findByWorkspaceAndHash(workspaceId: String, fileHash: String) = blob(workspaceId, fileHash)
    override suspend fun findBlobForUpdate(workspaceId: String, fileHash: String) = blob(workspaceId, fileHash)
    override suspend fun countActiveReferences(workspaceId: String, fileHash: String) = 0
    override suspend fun markReadyForGC(workspaceId: String, fileHash: String, orphanedAt: Instant) {
        saveBlob(
            requireNotNull(blob(workspaceId, fileHash))
                .copy(status = BlobStatus.READY_FOR_GC, orphanedAt = orphanedAt),
        )
    }
    override suspend fun markAsGarbageCollected(workspaceId: String, fileHash: String) {
        saveBlob(
            requireNotNull(blob(workspaceId, fileHash))
                .copy(status = BlobStatus.GARBAGE_COLLECTED, failureReason = null),
        )
    }
    override suspend fun findReadyForGC(threshold: Instant, batchSize: Int): Flow<WorkspaceFileBlob> =
        blobs.values.filter {
            it.status == BlobStatus.READY_FOR_GC &&
                (it.orphanedAt?.isBefore(threshold) == true) &&
                it.gcFailureCount < MediaAsset.GC_MAX_FAILURE_COUNT
        }.take(batchSize).asFlow()
    override suspend fun recordGCFailure(workspaceId: String, fileHash: String, failureReason: String) {
        val blob = requireNotNull(blob(workspaceId, fileHash))
        saveBlob(
            blob.copy(
                gcFailureCount = blob.gcFailureCount + 1,
                failureReason = failureReason,
                lastGcAttemptAt = Instant.now(),
            ),
        )
    }
    override suspend fun markBlobReady(
        workspaceId: String,
        fileHash: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long,
    ) {
        saveBlob(
            requireNotNull(blob(workspaceId, fileHash)).copy(
                status = BlobStatus.READY,
                storageKey = storageKey,
                detectedMediaType = detectedMediaType,
                fileSizeBytes = fileSizeBytes,
                failureReason = null,
                orphanedAt = null,
            ),
        )
    }
    override suspend fun resetBlobToUploading(workspaceId: String, fileHash: String) {
        saveBlob(
            requireNotNull(blob(workspaceId, fileHash)).copy(
                status = BlobStatus.UPLOADING,
                storageKey = null,
                detectedMediaType = null,
                fileSizeBytes = null,
                failureReason = null,
                orphanedAt = null,
                gcFailureCount = 0,
            ),
        )
    }
    override suspend fun markBlobFailed(workspaceId: String, fileHash: String, failureReason: String) {
        saveBlob(
            requireNotNull(blob(workspaceId, fileHash))
                .copy(status = BlobStatus.FAILED, failureReason = failureReason),
        )
    }
}

private class InMemoryRateLimitRepository(private val allowCreates: Boolean = true) : MediaRateLimitRepository {
    override suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int) = true
    override suspend fun releaseConcurrentUploadSlot(workspaceId: String) = Unit
    override suspend fun tryIncrementHourlyCreationCount(workspaceId: String, maxPerHour: Int) = allowCreates
}

private class FakeStorage : Storage {
    val uploaded = linkedMapOf<String, List<ByteArray>>()
    val deletedKeys = mutableListOf<String>()
    val copies = mutableListOf<Pair<String, String>>()
    fun service() = StorageApplicationService(this, NoopEventPublisher(), NoopStorageObservation())
    override suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String, String>) {
        uploaded[key] = content.toList()
    }
    override fun download(bucket: String, key: String): Flow<ByteArray> = flowOf()
    override suspend fun delete(bucket: String, key: String) {
        deletedKeys += key
    }
    override suspend fun list(bucket: String, prefix: String) = emptyList<String>()
    override suspend fun exists(bucket: String, key: String) = key in uploaded
    override suspend fun copyObject(bucket: String, sourceKey: String, destKey: String) {
        if (sourceKey !in uploaded) throw IllegalStateException("copyObject: source not found: $sourceKey")
        copies += sourceKey to destKey
    }
}

private class NoopEventPublisher : EventPublisher<BaseDomainEvent> {
    override suspend fun publish(event: BaseDomainEvent) = Unit
    override suspend fun publish(events: List<BaseDomainEvent>) = Unit
}

private class NoopStorageObservation : StorageObservation {
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

private fun putHandler(
    media: InMemoryMediaAssetRepository,
    blobs: InMemoryWorkspaceFileBlobRepository,
    limiter: InMemoryRateLimitRepository = InMemoryRateLimitRepository(),
) = PutAssetHandler(media, blobs, limiter, MediaUploadSettings(1, 200, "bucket"), NoopAtomicTransactionRunner)
private fun uploadHandler(
    media: InMemoryMediaAssetRepository,
    blobs: InMemoryWorkspaceFileBlobRepository,
    storage: FakeStorage,
) = CasUploadAssetHandler(
    media,
    blobs,
    storage.service(),
    MediaUploadSettings(1, 200, "bucket"),
    NoopAtomicTransactionRunner,
)
private fun deleteHandler(media: InMemoryMediaAssetRepository, blobs: InMemoryWorkspaceFileBlobRepository) =
    DeleteAssetHandler(media, blobs, NoopAtomicTransactionRunner)

/**
 * No-op `AtomicTransactionRunner` for handler unit tests. Real transactional behaviour
 * (the `FOR UPDATE` row lock, atomicity, etc.) is covered separately by Postgres Testcontainers
 * integration tests. This stub satisfies the application-layer port without depending on the
 * infrastructure-layer R2DBC implementation.
 */
private object NoopAtomicTransactionRunner : AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
}
private fun reconcilerSettings() = MediaReconcilerSettings("bucket", 2, 30)
private fun putCommand(
    assetId: String,
    fileHash: String,
    fileSizeBytes: Long = 1024,
    mediaType: String = "image/jpeg",
) = PutAssetCommand(assetId, WORKSPACE, fileHash, fileSizeBytes, mediaType, "photo.jpg")
private fun uploadCommand(
    assetId: String,
    hash: String,
    bytes: ByteArray,
    declaredSize: Long = bytes.size.toLong(),
    declaredType: String = "image/jpeg",
) = CasUploadAssetCommand(assetId, WORKSPACE, flowOf(bytes), hash, declaredSize, declaredType)
private fun pendingAsset(
    assetId: String,
    hash: String,
    mediaType: String = "image/jpeg",
    fileSizeBytes: Long = 1024,
    status: MediaAssetStatus = MediaAssetStatus.PENDING_UPLOAD,
) = MediaAsset(
    assetId,
    WORKSPACE,
    MediaSourceType.UPLOADED,
    hash,
    mediaType,
    null,
    originalFilename = if (mediaType == "image/png") "photo.png" else "photo.jpg",
    fileSizeBytes = fileSizeBytes,
    status = status,
    createdAt = Instant.now(),
)
private fun readyAsset(assetId: String, hash: String) = MediaAsset(
    assetId,
    WORKSPACE,
    MediaSourceType.UPLOADED,
    hash,
    "image/jpeg",
    "assets/$WORKSPACE/blobs/$hash.jpg",
    detectedMediaType = "image/jpeg",
    originalFilename = "photo.jpg",
    fileSizeBytes = 1024,
    status = MediaAssetStatus.READY,
    createdAt = Instant.now(),
)
private fun uploadingBlob(hash: String, workspaceId: String = WORKSPACE) =
    WorkspaceFileBlob(workspaceId, hash, null, null, null, BlobStatus.UPLOADING, createdAt = Instant.now())
private fun readyBlob(hash: String, fileSizeBytes: Long = 1024) = WorkspaceFileBlob(
    WORKSPACE,
    hash,
    "assets/$WORKSPACE/blobs/$hash.jpg",
    fileSizeBytes,
    "image/jpeg",
    BlobStatus.READY,
    createdAt = Instant.now(),
)
private fun jpegBytes() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02)
private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private const val WORKSPACE = "ws-test"
private const val ASSET_A = "550e8400-e29b-41d4-a716-446655440000"
private const val ASSET_B = "550e8400-e29b-41d4-a716-446655440001"
private const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
private const val HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
