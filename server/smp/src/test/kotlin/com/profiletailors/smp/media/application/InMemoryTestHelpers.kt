package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.media.domain.BlobStatus
import com.profiletailors.smp.media.domain.BlobUpsertResult
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.WorkspaceFileBlob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.time.Instant

/**
 * Test-only [MediaAssetRepository] kept in the same package so multiple test
 * files may share it without making the [MediaCasHandlersTest] helpers public.
 */
internal class ImportTestMediaAssetRepository : MediaAssetRepository {
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

    override suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean = false

    override suspend fun claimCasUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean = false

    override suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset? {
        val a = asset(workspaceId, assetId) ?: return null
        val updated = a.copy(status = MediaAssetStatus.READY, fileSizeBytes = fileSizeBytes)
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
        val a = asset(workspaceId, assetId) ?: return null
        val updated = a.copy(
            status = MediaAssetStatus.READY,
            storageKey = storageKey,
            detectedMediaType = detectedMediaType,
            fileSizeBytes = fileSizeBytes ?: a.fileSizeBytes,
        )
        assets[workspaceId to assetId] = updated
        return updated
    }

    override suspend fun markAsFailed(assetId: String, workspaceId: String, reason: String?): MediaAsset? = null
    override suspend fun softDelete(assetId: String, workspaceId: String): MediaAsset? = null
    override suspend fun findStaleProcessingAssets(thresholdHours: Long, gracePeriodMinutes: Long) =
        emptyList<MediaAsset>()
    override suspend fun findRecentlyFailedAssets() = emptyList<MediaAsset>()
    override suspend fun findExpiredPendingUploadAssets(limit: Int) = emptyList<MediaAsset>()
    override suspend fun findExpiredUploadingAssets(limit: Int) = emptyList<MediaAsset>()
    override suspend fun countActiveReferences(workspaceId: String, fileHash: String): Int = assets.values.count {
        it.workspaceId == workspaceId &&
            it.fileHash == fileHash &&
            it.status !in setOf(MediaAssetStatus.DELETED, MediaAssetStatus.FAILED)
    }

    override suspend fun findActiveByWorkspaceAndHash(workspaceId: String, fileHash: String): MediaAsset? =
        assets.values
            .filter {
                it.workspaceId == workspaceId &&
                    it.fileHash == fileHash &&
                    it.status !in setOf(MediaAssetStatus.DELETED, MediaAssetStatus.FAILED)
            }
            .minByOrNull { it.createdAt }
}

/**
 * Test-only [WorkspaceFileBlobRepository] shared across test files.
 */
internal class ImportTestBlobRepository(
    private val backing: MutableMap<Pair<String, String>, WorkspaceFileBlob> = linkedMapOf(),
) : WorkspaceFileBlobRepository {

    val blobs: Map<Pair<String, String>, WorkspaceFileBlob> get() = backing
    fun blob(workspaceId: String, fileHash: String) = backing[workspaceId to fileHash]

    fun put(blob: WorkspaceFileBlob) {
        backing[blob.workspaceId to blob.fileHash] = blob
    }

    override suspend fun upsertBlob(workspaceId: String, fileHash: String): BlobUpsertResult {
        val existing = blob(workspaceId, fileHash)
        if (existing != null) return BlobUpsertResult.Existed(existing)
        val created = WorkspaceFileBlob(
            workspaceId = workspaceId,
            fileHash = fileHash,
            storageKey = null,
            fileSizeBytes = null,
            detectedMediaType = null,
            status = BlobStatus.UPLOADING,
            createdAt = Instant.now(),
        )
        backing[workspaceId to fileHash] = created
        return BlobUpsertResult.Created(created)
    }

    override suspend fun findByWorkspaceAndHash(workspaceId: String, fileHash: String): WorkspaceFileBlob? =
        blob(workspaceId, fileHash)

    override suspend fun findBlobForUpdate(workspaceId: String, fileHash: String): WorkspaceFileBlob? =
        blob(workspaceId, fileHash)

    override suspend fun countActiveReferences(workspaceId: String, fileHash: String): Int = 0

    override suspend fun markReadyForGC(workspaceId: String, fileHash: String, orphanedAt: Instant) {
        val b = blob(workspaceId, fileHash) ?: return
        backing[workspaceId to fileHash] = b.copy(status = BlobStatus.READY_FOR_GC, orphanedAt = orphanedAt)
    }

    override suspend fun markAsGarbageCollected(workspaceId: String, fileHash: String) {
        val b = blob(workspaceId, fileHash) ?: return
        backing[workspaceId to fileHash] = b.copy(status = BlobStatus.GARBAGE_COLLECTED, failureReason = null)
    }

    override suspend fun findReadyForGC(threshold: Instant, batchSize: Int): Flow<WorkspaceFileBlob> =
        backing.values.take(batchSize).asFlow()

    override suspend fun recordGCFailure(workspaceId: String, fileHash: String, failureReason: String) {
        val b = blob(workspaceId, fileHash) ?: return
        backing[workspaceId to fileHash] = b.copy(
            gcFailureCount = b.gcFailureCount + 1,
            failureReason = failureReason,
            lastGcAttemptAt = Instant.now(),
        )
    }

    override suspend fun markBlobReady(
        workspaceId: String,
        fileHash: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long,
    ) {
        val b = blob(workspaceId, fileHash) ?: return
        backing[workspaceId to fileHash] = b.copy(
            status = BlobStatus.READY,
            storageKey = storageKey,
            detectedMediaType = detectedMediaType,
            fileSizeBytes = fileSizeBytes,
            failureReason = null,
            orphanedAt = null,
        )
    }

    override suspend fun resetBlobToUploading(workspaceId: String, fileHash: String) {
        val b = blob(workspaceId, fileHash) ?: return
        backing[workspaceId to fileHash] = b.copy(
            status = BlobStatus.UPLOADING,
            storageKey = null,
            detectedMediaType = null,
            fileSizeBytes = null,
            failureReason = null,
            orphanedAt = null,
            gcFailureCount = 0,
        )
    }

    override suspend fun markBlobFailed(workspaceId: String, fileHash: String, failureReason: String) {
        val b = blob(workspaceId, fileHash) ?: return
        backing[workspaceId to fileHash] = b.copy(status = BlobStatus.FAILED, failureReason = failureReason)
    }
}

/**
 * Test-only rate-limit repository; defaults to always-allow unless overridden.
 */
internal class ImportTestRateLimitRepository(
    private val allowConcurrent: Boolean = true,
    private val allowCreations: Boolean = true,
) : MediaRateLimitRepository {
    var released: Int = 0
        private set
    override suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int): Boolean =
        allowConcurrent
    override suspend fun releaseConcurrentUploadSlot(workspaceId: String) {
        released++
    }
    override suspend fun tryIncrementHourlyCreationCount(workspaceId: String, maxPerHour: Int): Boolean = allowCreations
}

/**
 * (No-op [EventPublisher] is provided locally by ImportExternalAssetHandlerTest.kt)
 */
