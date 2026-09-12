package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.observability.NoOpOperationalEventSink
import com.profiletailors.observability.OperationalEventSink
import com.profiletailors.observability.Severity
import com.profiletailors.observability.emit
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAsset.Companion.GC_RETENTION_DAYS
import com.profiletailors.smp.media.domain.WorkspaceFileBlob
import com.profiletailors.storage.domain.StorageException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import java.time.Instant

private const val ASSET_ID_ATTRIBUTE = "assetId"
private const val WORKSPACE_ID_ATTRIBUTE = "workspaceId"
private const val FILE_HASH_ATTRIBUTE = "fileHash"
private const val MEDIA_EXPIRATION_EVENT_PREFIX = "media.expiration"

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
    private val storage: MediaStorage,
    private val reconcilerSettings: MediaReconcilerSettings,
    private val operationalEvents: OperationalEventSink = NoOpOperationalEventSink,
) {

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
            operationalEvents.emit(
                severity = Severity.ERROR,
                name = "media.gc.run.failed",
                cause = e,
            )
        }

        val durationMs = System.currentTimeMillis() - startTime
        operationalEvents.emit(
            severity = Severity.INFO,
            name = "media.gc.run",
            attributes = arrayOf(
                "blobsScanned" to blobsScanned,
                "blobsDeleted" to blobsDeleted,
                "storageErrors" to storageErrors,
                "skippedBlobs" to skippedBlobs,
                "durationMs" to durationMs,
            ),
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
            operationalEvents.emit(
                severity = Severity.WARN,
                name = "media.gc.skip.noStorageKey",
                attributes = arrayOf(
                    WORKSPACE_ID_ATTRIBUTE to blob.workspaceId,
                    FILE_HASH_ATTRIBUTE to blob.fileHash,
                ),
            )
            return BlobGCResult.Skipped
        }

        return try {
            withTimeout(GC_LOCK_TIMEOUT_MILLIS) {
                storage.delete(
                    bucket = reconcilerSettings.storageBucket,
                    key = storageKey,
                    deleterId = "blob-gc",
                )
            }

            workspaceFileBlobRepository.markAsGarbageCollected(blob.workspaceId, blob.fileHash)

            operationalEvents.emit(
                severity = Severity.INFO,
                name = "media.gc.deleted",
                attributes = arrayOf(
                    WORKSPACE_ID_ATTRIBUTE to blob.workspaceId,
                    FILE_HASH_ATTRIBUTE to blob.fileHash,
                    "storageKey" to storageKey,
                ),
            )
            BlobGCResult.Deleted
        } catch (e: StorageException) {
            workspaceFileBlobRepository.recordGCFailure(
                blob.workspaceId,
                blob.fileHash,
                "storage.delete.failed: ${e.message.orEmpty()}",
            )
            operationalEvents.emit(
                severity = Severity.WARN,
                name = "media.gc.storageFailed",
                cause = e,
                attributes = arrayOf(
                    WORKSPACE_ID_ATTRIBUTE to blob.workspaceId,
                    FILE_HASH_ATTRIBUTE to blob.fileHash,
                    "storageKey" to storageKey,
                ),
            )
            BlobGCResult.StorageFailed
        } catch (e: TimeoutCancellationException) {
            workspaceFileBlobRepository.recordGCFailure(
                blob.workspaceId,
                blob.fileHash,
                "storage.delete.timeout",
            )
            operationalEvents.emit(
                severity = Severity.WARN,
                name = "media.gc.storageTimeout",
                cause = e,
                attributes = arrayOf(
                    WORKSPACE_ID_ATTRIBUTE to blob.workspaceId,
                    FILE_HASH_ATTRIBUTE to blob.fileHash,
                    "storageKey" to storageKey,
                ),
            )
            BlobGCResult.StorageFailed
        } catch (e: RuntimeException) {
            // Defensive: unexpected runtime errors should not silently disappear
            workspaceFileBlobRepository.recordGCFailure(
                blob.workspaceId,
                blob.fileHash,
                "gc.error: ${e.message.orEmpty()}",
            )
            operationalEvents.emit(
                severity = Severity.ERROR,
                name = "media.gc.error",
                cause = e,
                attributes = arrayOf(
                    WORKSPACE_ID_ATTRIBUTE to blob.workspaceId,
                    FILE_HASH_ATTRIBUTE to blob.fileHash,
                ),
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
    private val operationalEvents: OperationalEventSink = NoOpOperationalEventSink,
) {

    companion object {
        private const val BATCH_SIZE = 100
    }

    /**
     * Run one expiration cycle.
     */
    suspend fun run(): ExpirationRunResult {
        val startTime = System.currentTimeMillis()
        val outcome = runExpirationCycle()
        val durationMs = System.currentTimeMillis() - startTime
        operationalEvents.emit(
            severity = Severity.INFO,
            name = "$MEDIA_EXPIRATION_EVENT_PREFIX.run",
            attributes = arrayOf(
                "pendingExpired" to outcome.pendingExpired,
                "uploadingExpired" to outcome.uploadingExpired,
                "blobsScheduledForGC" to outcome.blobsScheduledForGC,
                "errors" to outcome.errors,
                "durationMs" to durationMs,
            ),
        )
        return ExpirationRunResult(
            pendingExpired = outcome.pendingExpired,
            uploadingExpired = outcome.uploadingExpired,
            blobsScheduledForGC = outcome.blobsScheduledForGC,
            errors = outcome.errors,
            durationMs = durationMs,
            timestamp = Instant.now(),
        )
    }

    private suspend fun runExpirationCycle(): ExpirationOutcome {
        var pendingExpired = 0
        var uploadingExpired = 0
        var blobsScheduledForGC = 0
        var errors = 0
        try {
            val pending = expirePendingAssets()
            pendingExpired = pending.expired
            blobsScheduledForGC += pending.scheduledForGc
            errors += pending.errors
            val uploading = expireUploadingAssets()
            uploadingExpired = uploading.expired
            blobsScheduledForGC += uploading.scheduledForGc
            errors += uploading.errors
        } catch (e: IllegalStateException) {
            errors++
            operationalEvents.emit(
                severity = Severity.ERROR,
                name = "$MEDIA_EXPIRATION_EVENT_PREFIX.run.failed",
                cause = e,
            )
        }
        return ExpirationOutcome(pendingExpired, uploadingExpired, blobsScheduledForGC, errors)
    }

    private suspend fun expirePendingAssets(): ExpirationCounts {
        var expired = 0
        var scheduledForGc = 0
        var errors = 0
        for (asset in mediaAssetRepository.findExpiredPendingUploadAssets(BATCH_SIZE)) {
            try {
                if (expirePendingUploadAsset(asset)) scheduledForGc++
                expired++
            } catch (e: IllegalStateException) {
                errors++
                emitExpirationFailure("media.expiration.pendingUpload.failed", asset, e)
            }
        }
        return ExpirationCounts(expired, scheduledForGc, errors)
    }

    private suspend fun expireUploadingAssets(): ExpirationCounts {
        var expired = 0
        var scheduledForGc = 0
        var errors = 0
        for (asset in mediaAssetRepository.findExpiredUploadingAssets(BATCH_SIZE)) {
            try {
                if (expireUploadingAsset(asset)) scheduledForGc++
                expired++
            } catch (e: IllegalStateException) {
                errors++
                emitExpirationFailure("media.expiration.uploading.failed", asset, e)
            }
        }
        return ExpirationCounts(expired, scheduledForGc, errors)
    }

    private fun emitExpirationFailure(name: String, asset: MediaAsset, cause: Throwable) {
        operationalEvents.emit(
            severity = Severity.ERROR,
            name = name,
            cause = cause,
            attributes = arrayOf(ASSET_ID_ATTRIBUTE to asset.assetId),
        )
    }

    private suspend fun expirePendingUploadAsset(asset: MediaAsset): Boolean {
        mediaAssetRepository.markAsFailed(asset.assetId, asset.workspaceId, "expired:pending_upload_ttl")
        releaseUploadSlot(asset)
        operationalEvents.emit(
            severity = Severity.INFO,
            name = "media.asset.expired.pendingUpload",
            attributes = arrayOf(
                ASSET_ID_ATTRIBUTE to asset.assetId,
                WORKSPACE_ID_ATTRIBUTE to asset.workspaceId,
            ),
        )

        // Check if blob needs GC
        val fileHash = asset.fileHash ?: return false
        return scheduleBlobGCIfOrphaned(asset.workspaceId, fileHash)
    }

    private suspend fun expireUploadingAsset(asset: MediaAsset): Boolean {
        mediaAssetRepository.markAsFailed(asset.assetId, asset.workspaceId, "expired:uploading_ttl")
        releaseUploadSlot(asset)
        operationalEvents.emit(
            severity = Severity.INFO,
            name = "media.asset.expired.uploading",
            attributes = arrayOf(
                ASSET_ID_ATTRIBUTE to asset.assetId,
                WORKSPACE_ID_ATTRIBUTE to asset.workspaceId,
            ),
        )

        // Check if blob needs GC
        val fileHash = asset.fileHash ?: return false
        return scheduleBlobGCIfOrphaned(asset.workspaceId, fileHash)
    }

    private suspend fun scheduleBlobGCIfOrphaned(workspaceId: String, fileHash: String): Boolean {
        // Run findBlobForUpdate + countActiveReferences + markReadyForGC in a single
        // transaction so FOR UPDATE actually locks the row for the whole decision window.
        return transactionRunner.runAtomically {
            workspaceFileBlobRepository.findBlobForUpdate(workspaceId, fileHash)
                ?: return@runAtomically false

            val activeCount = mediaAssetRepository.countActiveReferences(workspaceId, fileHash)
            if (activeCount == 0) {
                val orphanedAt = Instant.now()
                workspaceFileBlobRepository.markReadyForGC(workspaceId, fileHash, orphanedAt)
                operationalEvents.emit(
                    severity = Severity.INFO,
                    name = "media.blob.expired.markedReadyForGC",
                    attributes = arrayOf(
                        WORKSPACE_ID_ATTRIBUTE to workspaceId,
                        FILE_HASH_ATTRIBUTE to fileHash,
                    ),
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
            operationalEvents.emit(
                severity = Severity.DEBUG,
                name = "media.expiration.uploadSlotReleaseFailed",
                cause = e,
                attributes = arrayOf(ASSET_ID_ATTRIBUTE to asset.assetId),
            )
        }
    }
}

private data class ExpirationCounts(val expired: Int, val scheduledForGc: Int, val errors: Int)

private data class ExpirationOutcome(
    val pendingExpired: Int,
    val uploadingExpired: Int,
    val blobsScheduledForGC: Int,
    val errors: Int,
)

data class ExpirationRunResult(
    val pendingExpired: Int,
    val uploadingExpired: Int,
    val blobsScheduledForGC: Int,
    val errors: Int,
    val durationMs: Long,
    val timestamp: Instant,
)
