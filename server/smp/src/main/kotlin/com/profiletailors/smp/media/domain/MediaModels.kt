package com.profiletailors.smp.media.domain

import java.time.Instant
import java.util.UUID

/**
 * Source type for media assets.
 */
enum class MediaSourceType {
    /**
     * Asset uploaded through the media library upload flow.
     */
    UPLOADED,

    /**
     * Asset referenced from an external URL (deferred for post-MVP).
     */
    EXTERNAL_URL,
}

/**
 * Lifecycle status for media assets.
 *
 * State transitions:
 * - PROCESSING → READY (upload completes successfully)
 * - PROCESSING → FAILED (upload fails, is interrupted, times out, or stale reconciler cleans up)
 * - FAILED → PROCESSING (client retries upload)
 */
enum class MediaAssetStatus {
    /**
     * Asset created but upload not yet completed, or upload in progress.
     */
    PROCESSING,

    /**
     * Binary successfully stored and asset available for selection/publishing.
     */
    READY,

    /**
     * Upload failed, was interrupted, timed out, or was cleaned up by the stale reconciler.
     * Assets in this state are retryable.
     */
    FAILED,
}

/**
 * Workspace-scoped media asset aggregate.
 *
 * Represents a media asset owned by the media bounded context. For the MVP, only
 * UPLOADED source type is supported through the media library API.
 *
 * @property assetId Unique identifier for the asset (UUID v4). MUST NOT be sequential or predictable.
 * @property workspaceId The workspace that owns this asset.
 * @property sourceType How the asset was created (UPLOADED for MVP).
 * @property mediaType MIME type of the asset (e.g., "image/jpeg").
 * @property storageKey Backend-generated stable storage key for uploaded assets
 *   (format: assets/{workspaceId}/{assetId}).
 * @property originalFilename Original filename provided by the client (required for OOXML formats).
 * @property fileSizeBytes Size of the uploaded file in bytes (captured after successful upload).
 * @property status Lifecycle status (PROCESSING, READY, FAILED).
 * @property uploadStartedAt Timestamp when the upload handler began streaming. Used by the reconciler
 *                           to apply the grace period and by the conflict check to detect in-flight uploads.
 *                           Reset to NULL when the asset transitions to FAILED to keep FAILED assets
 *                           immediately retryable.
 * @property createdAt When the asset record was created.
 */
data class MediaAsset(
    val assetId: String,
    val workspaceId: String,
    val sourceType: MediaSourceType,
    val mediaType: String,
    val storageKey: String,
    val originalFilename: String? = null,
    val fileSizeBytes: Long? = null,
    val status: MediaAssetStatus,
    val uploadStartedAt: Instant? = null,
    val createdAt: Instant,
) {
    init {
        require(assetId.isNotBlank()) { "Asset ID must not be blank" }
        require(workspaceId.isNotBlank()) { "Workspace ID must not be blank" }
        require(mediaType.isNotBlank()) { "Media type must not be blank" }
        require(storageKey.isNotBlank()) { "Storage key must not be blank" }

        // For MVP, only UPLOADED is supported
        require(sourceType == MediaSourceType.UPLOADED) {
            "Only UPLOADED source type is supported in the MVP"
        }

        // OOXML formats require originalFilename
        if (mediaType in OFFICE_DOCUMENT_MEDIA_TYPES) {
            require(!originalFilename.isNullOrBlank()) {
                "originalFilename is required for OOXML media types"
            }
        }

        // fileSizeBytes is only set after successful upload
        if (status == MediaAssetStatus.READY) {
            require(fileSizeBytes != null && fileSizeBytes > 0) {
                "READY assets must have a valid file size"
            }
        }
    }

    companion object {
        /**
         * Supported media types for the MVP.
         */
        val SUPPORTED_MEDIA_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "video/mp4",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )

        /**
         * Office document media types (legacy binary and OOXML) that require originalFilename.
         */
        val OFFICE_DOCUMENT_MEDIA_TYPES = setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )

        /**
         * Maximum file size in bytes (500 MB).
         */
        const val MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024

        /**
         * Maximum upload duration in milliseconds (10 minutes).
         */
        const val MAX_UPLOAD_DURATION_MS = 10L * 60 * 1000

        /**
         * Grace period for in-flight uploads (30 minutes).
         */
        const val UPLOAD_IN_FLIGHT_GRACE_PERIOD_MINUTES = 30L

        /**
         * Stale asset threshold (2 hours).
         */
        const val STALE_ASSET_THRESHOLD_HOURS = 2L

        /**
         * Generate a deterministic storage key for an asset.
         */
        fun generateStorageKey(workspaceId: String, assetId: String): String =
            "assets/$workspaceId/$assetId"

        /**
         * Generate a new UUID v4 asset identifier.
         */
        fun generateAssetId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Resolved media asset returned by the MediaAssetResolver port.
 *
 * This is the contract exposed to publishing and other consumers of the media context.
 * The status field is omitted because the port contract guarantees all returned assets are READY.
 *
 * @property assetId The asset identifier.
 * @property workspaceId The workspace that owns this asset.
 * @property storageKey The storage key for retrieving the binary.
 * @property mediaType MIME type of the asset.
 */
data class ResolvedMediaAsset(
    val assetId: String,
    val workspaceId: String,
    val storageKey: String,
    val mediaType: String,
)
