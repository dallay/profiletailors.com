package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAsset.Companion.GC_RETENTION_DAYS
import com.profiletailors.smp.media.domain.WorkspaceFileBlob
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.StorageException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Blob Garbage Collector.
 *
 * Runs hourly and physically deletes storage objects for orphaned blobs that have
 * exceeded the 7-day retention period.
 *
 * The job NEVER deletes `workspace_file_blobs` rows — it only updates status to
 * `GARBAGE_COLLECTED` and deletes the storage object.
 *
 * Uses `FOR UPDATE SKIP LOCKED` for safe concurrent processing across multiple instances.
 *
 * Blobs with `gc_failure_count >= 5` are skipped — they require manual intervention.
 */
@Service
class BlobGarbageCollector(
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
    private val mediaAssetRepository: MediaAssetRepository,
    private val storageApplicationService: StorageApplicationService,
    private val reconcilerSettings: MediaReconcilerSettings,
) {
    private val logger = LoggerFactory.getLogger(BlobGarbageCollector::class.java)

    companion object {
        private const val BATCH_SIZE = 100
        private const val GC_LOCK_TIMEOUT_MILLIS = 30_000L
    }

    /**
     * Run one GC cycle.
     * Public for testing and manual trigger.
     * @return GCRunResult with metrics.
     */
    suspend fun run(): GCRunResult {
        val startTime = System.currentTimeMillis()
        var blobsScanned = 0
        var blobsDeleted = 0
        var storageErrors = 0
        var skippedBlobs = 0

        val threshold = Instant.now().minusSeconds(GC_RETENTION_DAYS * 24 * 3600)

        try {
            workspaceFileBlobRepository.findReadyForGC(threshold, BATCH_SIZE)
                .onEach { blobsScanned++ }
                .collect { blob ->
                    val result = processBlob(blob)
                    when (result) {
                        BlobGCResult.Deleted -> blobsDeleted++
                        BlobGCResult.StorageFailed -> storageErrors++
                        BlobGCResult.Skipped -> skippedBlobs++
                    }
                }
        } catch (e: IllegalStateException) {
            logger.error("BlobGarbageCollector run failed", e)
        }

        val durationMs = System.currentTimeMillis() - startTime
        logger.info(
            "media.gc.run blobsScanned={} blobsDeleted={} storageErrors={} skippedBlobs={} durationMs={}",
            blobsScanned,
            blobsDeleted,
            storageErrors,
            skippedBlobs,
            durationMs,
        )

        return GCRunResult(
            blobsScanned = blobsScanned,
            blobsDeleted = blobsDeleted,
            storageErrors = storageErrors,
            skippedBlobs = skippedBlobs,
            durationMs = durationMs,
            timestamp = Instant.now(),
        )
    }

    @Suppress("TooGenericExceptionCaught") // Defensive: catches unexpected RuntimeException from DB operations
    private suspend fun processBlob(blob: WorkspaceFileBlob): BlobGCResult {
        val storageKey = blob.storageKey
        if (storageKey.isNullOrBlank()) {
            logger.warn(
                "media.gc.skip.noStorageKey workspaceId={} fileHash={}",
                blob.workspaceId,
                blob.fileHash,
            )
            return BlobGCResult.Skipped
        }

        return try {
            withTimeout(GC_LOCK_TIMEOUT_MILLIS) {
                storageApplicationService.delete(
                    bucket = reconcilerSettings.storageBucket,
                    key = storageKey,
                    deleterId = "blob-gc",
                )
            }

            workspaceFileBlobRepository.markAsGarbageCollected(blob.workspaceId, blob.fileHash)

            logger.info(
                "media.gc.deleted workspaceId={} fileHash={} storageKey={}",
                blob.workspaceId,
                blob.fileHash,
                storageKey,
            )
            BlobGCResult.Deleted
        } catch (e: StorageException) {
            workspaceFileBlobRepository.recordGCFailure(
                blob.workspaceId,
                blob.fileHash,
                "storage.delete.failed: ${e.message}",
            )
            logger.warn(
                "media.gc.storageFailed workspaceId={} fileHash={} storageKey={} error={}",
                blob.workspaceId,
                blob.fileHash,
                storageKey,
                e.message,
            )
            BlobGCResult.StorageFailed
        } catch (e: TimeoutCancellationException) {
            workspaceFileBlobRepository.recordGCFailure(
                blob.workspaceId,
                blob.fileHash,
                "storage.delete.timeout",
            )
            logger.warn(
                "media.gc.storageTimeout workspaceId={} fileHash={} storageKey={}",
                blob.workspaceId,
                blob.fileHash,
                storageKey,
                e,
            )
            BlobGCResult.StorageFailed
        } catch (e: RuntimeException) {
            // Defensive: unexpected runtime errors should not silently disappear
            workspaceFileBlobRepository.recordGCFailure(
                blob.workspaceId,
                blob.fileHash,
                "gc.error: ${e.message}",
            )
            logger.error(
                "media.gc.error workspaceId={} fileHash={}",
                blob.workspaceId,
                blob.fileHash,
                e,
            )
            BlobGCResult.StorageFailed
        }
    }

    enum class BlobGCResult { Deleted, StorageFailed, Skipped }
}

data class GCRunResult(
    val blobsScanned: Int,
    val blobsDeleted: Int,
    val storageErrors: Int,
    val skippedBlobs: Int,
    val durationMs: Long,
    val timestamp: Instant,
)

/**
 * Media Asset Expiration Job.
 *
 * Runs every 6 hours and transitions stale PENDING_UPLOAD and UPLOADING assets to FAILED.
 * After marking the asset FAILED, it evaluates whether the underlying blob has any remaining
 * active references. If not, the blob is scheduled for GC.
 *
 * Handles both:
 * - PENDING_UPLOAD assets that were never started (>24h since creation)
 * - UPLOADING assets where the upload stalled (>24h since upload started)
 */
@Service
class MediaAssetExpirationJob(
    private val mediaAssetRepository: MediaAssetRepository,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val transactionRunner: AtomicTransactionRunner,
) {
    private val logger = LoggerFactory.getLogger(MediaAssetExpirationJob::class.java)

    companion object {
        private const val BATCH_SIZE = 100
    }

    /**
     * Run one expiration cycle.
     */
    suspend fun run(): ExpirationRunResult {
        val startTime = System.currentTimeMillis()
        var pendingExpired = 0
        var uploadingExpired = 0
        var blobsScheduledForGC = 0
        var errors = 0

        try {
            // Expire PENDING_UPLOAD assets
            val pendingAssets = mediaAssetRepository.findExpiredPendingUploadAssets(BATCH_SIZE)
            for (asset in pendingAssets) {
                try {
                    val scheduled = expirePendingUploadAsset(asset)
                    if (scheduled) blobsScheduledForGC++
                    pendingExpired++
                } catch (e: IllegalStateException) {
                    errors++
                    logger.error("Failed to expire PENDING_UPLOAD asset: {}", asset.assetId, e)
                }
            }

            // Expire UPLOADING assets
            val uploadingAssets = mediaAssetRepository.findExpiredUploadingAssets(BATCH_SIZE)
            for (asset in uploadingAssets) {
                try {
                    val scheduled = expireUploadingAsset(asset)
                    if (scheduled) blobsScheduledForGC++
                    uploadingExpired++
                } catch (e: IllegalStateException) {
                    errors++
                    logger.error("Failed to expire UPLOADING asset: {}", asset.assetId, e)
                }
            }
        } catch (e: IllegalStateException) {
            errors++
            logger.error("MediaAssetExpirationJob run failed", e)
        }

        val durationMs = System.currentTimeMillis() - startTime
        logger.info(
            "media.expiration.run pendingExpired={} uploadingExpired={} blobsScheduledForGC={} errors={} durationMs={}",
            pendingExpired,
            uploadingExpired,
            blobsScheduledForGC,
            errors,
            durationMs,
        )

        return ExpirationRunResult(
            pendingExpired = pendingExpired,
            uploadingExpired = uploadingExpired,
            blobsScheduledForGC = blobsScheduledForGC,
            errors = errors,
            durationMs = durationMs,
            timestamp = Instant.now(),
        )
    }

    private suspend fun expirePendingUploadAsset(asset: MediaAsset): Boolean {
        mediaAssetRepository.markAsFailed(asset.assetId, asset.workspaceId, "expired:pending_upload_ttl")
        releaseUploadSlot(asset)
        logger.info(
            "media.asset.expired.pendingUpload assetId={} workspaceId={}",
            asset.assetId,
            asset.workspaceId,
        )

        // Check if blob needs GC
        val fileHash = asset.fileHash ?: return false
        return scheduleBlobGCIfOrphaned(asset.workspaceId, fileHash)
    }

    private suspend fun expireUploadingAsset(asset: MediaAsset): Boolean {
        mediaAssetRepository.markAsFailed(asset.assetId, asset.workspaceId, "expired:uploading_ttl")
        releaseUploadSlot(asset)
        logger.info(
            "media.asset.expired.uploading assetId={} workspaceId={}",
            asset.assetId,
            asset.workspaceId,
        )

        // Check if blob needs GC
        val fileHash = asset.fileHash ?: return false
        return scheduleBlobGCIfOrphaned(asset.workspaceId, fileHash)
    }

    private suspend fun scheduleBlobGCIfOrphaned(workspaceId: String, fileHash: String): Boolean {
        // Run findBlobForUpdate + countActiveReferences + markReadyForGC in a single
        // transaction so FOR UPDATE actually locks the row for the whole decision window.
        return transactionRunner.runAtomically {
            val blob = workspaceFileBlobRepository.findBlobForUpdate(workspaceId, fileHash)
                ?: return@runAtomically false

            val activeCount = mediaAssetRepository.countActiveReferences(workspaceId, fileHash)
            if (activeCount == 0) {
                val orphanedAt = Instant.now()
                workspaceFileBlobRepository.markReadyForGC(workspaceId, fileHash, orphanedAt)
                logger.info(
                    "media.blob.expired.markedReadyForGC workspaceId={} fileHash={}",
                    workspaceId,
                    fileHash,
                )
                true
            } else {
                false
            }
        }
    }

    private suspend fun releaseUploadSlot(asset: MediaAsset) {
        try {
            mediaRateLimitRepository.releaseConcurrentUploadSlot(asset.workspaceId)
        } catch (e: IllegalStateException) {
            // No active upload slot — nothing to release, which is fine
            logger.debug("No upload slot to release for asset: {}", asset.assetId, e)
        }
    }
}

data class ExpirationRunResult(
    val pendingExpired: Int,
    val uploadingExpired: Int,
    val blobsScheduledForGC: Int,
    val errors: Int,
    val durationMs: Long,
    val timestamp: Instant,
)
