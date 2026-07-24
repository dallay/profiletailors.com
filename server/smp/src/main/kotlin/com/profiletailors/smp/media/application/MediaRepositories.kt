package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.domain.BlobUpsertResult
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.WorkspaceFileBlob
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository port for media asset persistence.
 */
interface MediaAssetRepository {
    /**
     * Create a new media asset.
     */
    suspend fun create(asset: MediaAsset): MediaAsset

    /**
     * Find an asset by workspace and asset ID.
     */
    suspend fun findByWorkspaceAndId(workspaceId: String, assetId: String): MediaAsset?

    /**
     * Find multiple assets by workspace and asset IDs.
     */
    suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: List<String>): List<MediaAsset>

    /**
     * List assets by workspace with filtering and pagination.
     */
    suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<MediaAssetStatus>,
        pageSize: Int,
        cursor: String?,
    ): PagedMediaAssets

    /**
     * Claim an upload slot for an asset using conditional update.
     *
     * For the legacy flow: UPDATE SET uploadStartedAt=NOW()
     * WHERE assetId=? AND workspaceId=? AND status IN ('PROCESSING','FAILED')
     * AND (uploadStartedAt IS NULL OR uploadStartedAt < NOW() - 30 minutes)
     *
     * For the CAS flow: use claimCasUploadSlot() instead.
     *
     * @return true if the update succeeded (1 row updated), false otherwise.
     */
    suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean

    /**
     * Claim an upload slot for a CAS asset (PENDING_UPLOAD or FAILED → UPLOADING).
     *
     * Updates: SET status='UPLOADING', uploadStartedAt=:now, updatedAt=CURRENT_TIMESTAMP
     * WHERE assetId=? AND workspaceId=? AND status IN ('PENDING_UPLOAD','FAILED')
     *
     * @return true if the update succeeded, false if no matching row.
     */
    suspend fun claimCasUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean

    /**
     * Update file size and transition to READY.
     */
    suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset?

    /**
     * Update asset to READY using blob metadata (for dedup fast-path).
     */
    suspend fun markAsReadyFromDedup(
        assetId: String,
        workspaceId: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long? = null,
    ): MediaAsset?

    /**
     * Transition an asset to FAILED and reset uploadStartedAt to NULL.
     * Optionally set a failure reason.
     */
    suspend fun markAsFailed(assetId: String, workspaceId: String, reason: String? = null): MediaAsset?

    /**
     * Update the status of a media asset.
     * Returns the updated asset, or null if the asset was not found.
     */
    suspend fun updateStatus(assetId: String, workspaceId: String, status: MediaAssetStatus): MediaAsset?

    /**
     * Soft-delete an asset: transition to DELETED status.
     * Does NOT physically delete the row.
     */
    suspend fun softDelete(assetId: String, workspaceId: String): MediaAsset?

    /**
     * Find stale PROCESSING assets older than the threshold with no recent upload activity.
     * (Legacy flow — only used by StaleAssetReconciler.)
     */
    suspend fun findStaleProcessingAssets(thresholdHours: Long, gracePeriodMinutes: Long): List<MediaAsset>

    /**
     * Find recently FAILED assets that may need storage cleanup retry.
     * Limits to assets created in the last 7 days to bound the scan.
     * (Legacy flow.)
     */
    suspend fun findRecentlyFailedAssets(): List<MediaAsset>

    /**
     * Find expired PENDING_UPLOAD assets (created_at < now - 24h).
     * Used by MediaAssetExpirationJob.
     */
    suspend fun findExpiredPendingUploadAssets(limit: Int): List<MediaAsset>

    /**
     * Find expired UPLOADING assets (uploadStartedAt < now - 24h).
     * Used by MediaAssetExpirationJob.
     */
    suspend fun findExpiredUploadingAssets(limit: Int): List<MediaAsset>

    /**
     * Count active (non-DELETED, non-FAILED) assets for a blob.
     * Used to decide whether to mark a blob READY_FOR_GC.
     */
    suspend fun countActiveReferences(workspaceId: String, fileHash: String): Int

    /**
     * Find an existing active (non-DELETED, non-FAILED) asset for the given (workspaceId, fileHash).
     * Used by the CAS dedup flow to return the canonical assetId when a client uploads a file
     * whose bytes already exist in the workspace — preventing duplicate asset rows for the same
     * physical content.
     *
     * @return the oldest active asset for that hash, or null if none exists.
     */
    suspend fun findActiveByWorkspaceAndHash(workspaceId: String, fileHash: String): MediaAsset?

    /**
     * Finds READY assets created before CAS migration that still have no file hash.
     * Used by MediaAssetBackfillJob.
     */
    suspend fun findReadyAssetsWithoutHash(limit: Int): List<MediaAsset> = emptyList()

    /**
     * Updates a READY asset with CAS metadata computed during backfill.
     */
    suspend fun updateReadyAssetCasMetadata(
        assetId: String,
        workspaceId: String,
        fileHash: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long,
    ): MediaAsset? = null
}

/**
 * Repository port for workspace-scoped content-addressed blob persistence.
 *
 * Each row is keyed by (workspace_id, file_hash). The row represents a single
 * physical blob stored at the canonical key once READY.
 */
interface WorkspaceFileBlobRepository {
    /**
     * Upsert a blob at PUT time.
     *
     * If a blob already exists for (workspaceId, fileHash), return it as Existed.
     * If not, insert a new row in UPLOADING state with null storage_key,
     * null detected_media_type, null file_size_bytes.
     *
     * @return BlobUpsertResult indicating whether a new row was created.
     */
    suspend fun upsertBlob(workspaceId: String, fileHash: String): BlobUpsertResult

    /**
     * Find a blob by (workspaceId, fileHash) without locking.
     */
    suspend fun findByWorkspaceAndHash(workspaceId: String, fileHash: String): WorkspaceFileBlob?

    /**
     * Find a blob by (workspaceId, fileHash) with FOR UPDATE lock.
     * Used by DELETE, expiration, and upload finalization to prevent races.
     */
    suspend fun findBlobForUpdate(workspaceId: String, fileHash: String): WorkspaceFileBlob?

    /**
     * Count how many active (non-DELETED, non-FAILED) assets reference this blob.
     */
    suspend fun countActiveReferences(workspaceId: String, fileHash: String): Int

    /**
     * Mark a blob as READY_FOR_GC and set orphaned_at.
     * Called when the last active asset is deleted or expires.
     */
    suspend fun markReadyForGC(workspaceId: String, fileHash: String, orphanedAt: Instant)

    /**
     * Mark a blob as GARBAGE_COLLECTED (after physical storage delete).
     * The row is NEVER deleted — only status is updated.
     */
    suspend fun markAsGarbageCollected(workspaceId: String, fileHash: String)

    /**
     * Find blobs eligible for garbage collection.
     *
     * Criteria:
     * - status = 'READY_FOR_GC'
     * - orphaned_at < now() - 7 days
     * - gc_failure_count < 5
     *
     * Ordered by orphaned_at ASC, LIMIT 100.
     * Uses FOR UPDATE SKIP LOCKED for safe concurrent processing.
     */
    suspend fun findReadyForGC(threshold: Instant, batchSize: Int): Flow<WorkspaceFileBlob>

    /**
     * Record a GC failure: increment gc_failure_count and update last_gc_attempt_at.
     * Also sets failure_reason.
     */
    suspend fun recordGCFailure(workspaceId: String, fileHash: String, failureReason: String)

    /**
     * Update a blob to READY after upload finalization.
     */
    suspend fun markBlobReady(
        workspaceId: String,
        fileHash: String,
        storageKey: String,
        detectedMediaType: String,
        fileSizeBytes: Long,
    )

    /**
     * Reset a blob to UPLOADING state for a retry.
     * Clears orphaned_at, resets gc_failure_count, clears storage_key and detected_media_type.
     * Used when a new asset retries a FAILED/READY_FOR_GC/GARBAGE_COLLECTED blob.
     */
    suspend fun resetBlobToUploading(workspaceId: String, fileHash: String)

    /**
     * Mark a blob as FAILED and record the failure reason.
     */
    suspend fun markBlobFailed(workspaceId: String, fileHash: String, failureReason: String)
}

/**
 * Paged result for media assets.
 */
data class PagedMediaAssets(val assets: List<MediaAsset>, val nextCursor: String?)

/**
 * Repository port for rate limiting.
 */
interface MediaRateLimitRepository {
    /**
     * Try to claim a concurrent upload slot for a workspace.
     *
     * @return true if the slot was claimed, false if the limit is exceeded.
     */
    suspend fun tryClaimConcurrentUploadSlot(workspaceId: String, maxConcurrent: Int): Boolean

    /**
     * Release a concurrent upload slot for a workspace.
     */
    suspend fun releaseConcurrentUploadSlot(workspaceId: String)

    /**
     * Result of [tryIncrementHourlyCreationCount]: carries the post-increment count
     * so callers can report the actual value in exceptions.
     */
    data class RateLimitIncrementResult(val value: Int, val isAllowed: Boolean)

    /**
     * Try to increment the hourly creation counter for a workspace.
     *
     * @return [RateLimitIncrementResult] with the post-increment count and whether the increment was allowed.
     */
    @Suppress("FunctionNameMaxLength")
    suspend fun tryIncrementHourlyCreationCount(workspaceId: String, maxPerHour: Int): RateLimitIncrementResult
}

/**
 * Port for media-related storage operations.
 * Defined in the application layer so that [ImportUnsplashPhotoHandler] depends only
 * on this interface, not on the concrete [com.profiletailors.storage.application.StorageApplicationService].
 */
interface MediaStoragePort {
    suspend fun upload(
        bucket: String,
        key: String,
        content: Flow<ByteArray>,
        uploaderId: String,
        metadata: Map<String, String>,
    )

    suspend fun delete(bucket: String, key: String, deleterId: String)
}
