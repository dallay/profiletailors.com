package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
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
     * Performs: UPDATE ... SET uploadStartedAt = NOW() WHERE assetId = ? AND workspaceId = ?
     *           AND (status IN ('PROCESSING', 'FAILED'))
     *           AND (uploadStartedAt IS NULL OR uploadStartedAt < NOW() - INTERVAL '30 minutes')
     *
     * @return true if the update succeeded (1 row updated), false otherwise.
     */
    suspend fun claimUploadSlot(assetId: String, workspaceId: String, now: Instant): Boolean

    /**
     * Update file size and transition to READY.
     */
    suspend fun markAsReady(assetId: String, workspaceId: String, fileSizeBytes: Long): MediaAsset?

    /**
     * Transition an asset to FAILED and reset uploadStartedAt to NULL.
     */
    suspend fun markAsFailed(assetId: String, workspaceId: String): MediaAsset?

    /**
     * Delete an asset row and return the previous aggregate when found.
     */
    suspend fun delete(assetId: String, workspaceId: String): MediaAsset?

    /**
     * Find stale PROCESSING assets older than the threshold with no recent upload activity.
     */
    suspend fun findStaleProcessingAssets(
        thresholdHours: Long,
        gracePeriodMinutes: Long,
    ): List<MediaAsset>

    /**
     * Find recently FAILED assets that may need storage cleanup retry.
     * Limits to assets created in the last 7 days to bound the scan.
     */
    suspend fun findRecentlyFailedAssets(): List<MediaAsset>
}

/**
 * Paged result for media assets.
 */
data class PagedMediaAssets(
    val assets: List<MediaAsset>,
    val nextCursor: String?,
)

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
     * Try to increment the hourly creation counter for a workspace.
     *
     * @return true if the increment succeeded, false if the limit is exceeded.
     */
    suspend fun tryIncrementHourlyCreationCount(workspaceId: String, maxPerHour: Int): Boolean
}
