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
     * Asset imported through an external provider.
     */
    EXTERNAL,
}

/**
 * Lifecycle status for media assets.
 *
 * State transitions in the CAS model:
 * - PENDING_UPLOAD → UPLOADING (upload begins)
 * - UPLOADING → READY (upload completes successfully)
 * - UPLOADING → FAILED (upload fails, interrupted, or expired)
 * - PENDING_UPLOAD → FAILED (TTL expired before upload started)
 * - READY → DELETED (user deletes the asset)
 * - FAILED → UPLOADING → READY (client retries upload)
 */
enum class MediaAssetStatus {
    /**
     * Asset created but upload not yet completed, or upload in progress.
     * @deprecated Legacy state — use PENDING_UPLOAD / UPLOADING for new assets.
     */
    PROCESSING,

    /**
     * Asset created, awaiting upload. Server has reserved a CAS slot but
     * no bytes have been received yet.
     */
    PENDING_UPLOAD,

    /**
     * Upload in progress or being retried after a failure.
     */
    UPLOADING,

    /**
     * Binary successfully stored at the canonical CAS key and asset available
     * for selection/publishing.
     */
    READY,

    /**
     * Upload failed, was interrupted, timed out, or was cleaned up by the
     * expiration job. Assets in this state are retryable.
     */
    FAILED,

    /**
     * Asset has been soft-deleted by the user. The blob row may or may not
     * be scheduled for GC depending on whether other active assets reference it.
     */
    DELETED,
}

/**
 * Lifecycle status for workspace-scoped content-addressed blobs.
 *
 * Blobs are the physical stored objects, keyed by (workspace_id, file_hash).
 * Multiple assets within the same workspace can reference the same blob.
 *
 * State transitions:
 * - UPLOADING → READY (first upload completes)
 * - UPLOADING → FAILED (upload fails)
 * - READY → READY_FOR_GC (last active asset deleted)
 * - READY_FOR_GC → GARBAGE_COLLECTED (GC job deletes storage object)
 * - FAILED → UPLOADING (retry of failed upload)
 * - READY_FOR_GC → UPLOADING (retry after last asset deleted)
 * - GARBAGE_COLLECTED → UPLOADING (retry after GC)
 */
enum class BlobStatus {
    /**
     * An upload is in progress for this blob. Only one upload can be UPLOADING
     * at a time per (workspace_id, file_hash).
     */
    UPLOADING,

    /**
     * The blob is verified and at the canonical CAS key. At least one READY
     * asset references it.
     */
    READY,

    /**
     * The most recent upload attempt failed. Retryable by any asset.
     */
    FAILED,

    /**
     * The blob has no active references (all assets are DELETED/FAILED) and
     * is awaiting GC. After 7 days of retention it will be physically deleted.
     */
    READY_FOR_GC,

    /**
     * The physical storage object has been deleted. The row persists to satisfy
     * the FK from soft-deleted assets.
     */
    GARBAGE_COLLECTED,
}

/**
 * Workspace-scoped media asset aggregate.
 *
 * Represents a media asset owned by the media bounded context. The asset references
 * a [WorkspaceFileBlob] by (workspaceId, fileHash). The asset's storage_key is
 * nullable until READY — the canonical storage key lives on the blob row.
 *
 * @property assetId Unique identifier for the asset (UUID v4).
 * @property workspaceId The workspace that owns this asset.
 * @property sourceType How the asset was created (UPLOADED for MVP).
 * @property fileHash SHA-256 of the file content (lowercase hex, 64 chars).
 * @property mediaType MIME type declared by the client at PUT time.
 *   After upload, this may differ from detectedMediaType if magic bytes
 *   revealed a different type.
 * @property storageKey Backend-generated stable storage key for READY assets.
 *   NULL for PENDING_UPLOAD, UPLOADING, FAILED, and DELETED assets.
 *   The canonical key lives on the blob row; this field on the asset is a
 *   convenience copy for fast lookups.
 * @property detectedMediaType MIME type detected by the server via magic bytes.
 *   Set at upload completion. May differ from mediaType.
 * @property originalFilename Original filename provided by the client (required for OOXML formats).
 * @property fileSizeBytes Size of the uploaded file in bytes (captured after successful upload).
 * @property status Lifecycle status.
 * @property failureReason Human-readable failure reason set when status transitions to FAILED.
 *   Examples: "HASH_MISMATCH", "FILE_SIZE_MISMATCH", "expired:pending_upload_ttl".
 * @property uploadStartedAt Timestamp when the upload handler began streaming.
 *   Used by the expiration job to apply the 24h TTL. Reset to NULL when the
 *   asset transitions to FAILED.
 * @property createdAt When the asset record was created.
 * @property updatedAt When the asset record was last modified.
 */
data class MediaAsset(
    val assetId: String,
    val workspaceId: String,
    val sourceType: MediaSourceType,
    val fileHash: String?,
    val mediaType: String,
    val storageKey: String?,
    val detectedMediaType: String? = null,
    val originalFilename: String? = null,
    val fileSizeBytes: Long? = null,
    val status: MediaAssetStatus,
    val failureReason: String? = null,
    val uploadStartedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val sourceProvider: String? = null,
    val externalId: String? = null,
    val sourceUrl: String? = null,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val metadata: Map<String, Any>? = null,
) {
    init {
        require(assetId.isNotBlank()) { "Asset ID must not be blank" }
        require(workspaceId.isNotBlank()) { "Workspace ID must not be blank" }
        require(mediaType.isNotBlank()) { "Media type must not be blank" }

        when (sourceType) {
            MediaSourceType.UPLOADED -> {
                require(sourceProvider == null) {
                    "UPLOADED assets must have null sourceProvider"
                }
            }
            MediaSourceType.EXTERNAL -> {
                require(!sourceProvider.isNullOrBlank()) {
                    "EXTERNAL assets must have non-blank sourceProvider"
                }
                require(!externalId.isNullOrBlank()) {
                    "EXTERNAL assets must have non-blank externalId"
                }
                require(!sourceUrl.isNullOrBlank()) {
                    "EXTERNAL assets must have non-blank sourceUrl"
                }
            }
        }

        if (!sourceProvider.isNullOrBlank()) {
            require(sourceProvider.matches(Regex("^[a-z][a-z0-9_]{0,31}$"))) {
                "sourceProvider must be lowercase snake_case, 1-32 chars, starting with a letter"
            }
        }

        // fileHash is nullable to support existing rows from before the CAS migration.
        // New assets must always provide fileHash at PUT time.
        if (fileHash != null) {
            require(fileHash.matches(SHA256_HASH_REGEX)) {
                "fileHash must be a lowercase 64-character SHA-256 hex string"
            }
        }

        // OOXML formats require originalFilename
        if (mediaType in OFFICE_DOCUMENT_MEDIA_TYPES) {
            require(!originalFilename.isNullOrBlank()) {
                "originalFilename is required for OOXML media types"
            }
        }

        // storageKey must be non-null when READY (enforced by DB CHECK constraint)
        if (status == MediaAssetStatus.READY) {
            require(storageKey != null && storageKey.isNotBlank()) {
                "READY assets must have a non-null storageKey"
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
         * TTL for PENDING_UPLOAD and UPLOADING assets (24 hours).
         */
        const val ASSET_EXPIRATION_TTL_HOURS = 24L

        /**
         * GC retention period after blob becomes orphaned (7 days).
         */
        const val GC_RETENTION_DAYS = 7L

        /**
         * Maximum number of GC retry attempts before manual intervention.
         */
        const val GC_MAX_FAILURE_COUNT = 5

        /**
         * Rate limit: maximum creations per workspace per hour.
         */
        const val MAX_CREATIONS_PER_HOUR = 200

        /**
         * Grace period for in-flight uploads (30 minutes) — only used by the
         * legacy upload flow. The new CAS flow uses the TTL instead.
         */
        const val UPLOAD_IN_FLIGHT_GRACE_PERIOD_MINUTES = 30L

        /**
         * Stale asset threshold (2 hours) — only used by the legacy stale reconciler.
         */
        const val STALE_ASSET_THRESHOLD_HOURS = 2L

        /**
         * Generate a new UUID v4 asset identifier.
         */
        fun generateAssetId(): String = UUID.randomUUID().toString()

        /**
         * Generate a legacy storage key for PROCESSING assets.
         * @deprecated Use MediaStorageKeys.canonicalKey() or MediaStorageKeys.tempKey() for CAS assets.
         */
        fun generateStorageKey(workspaceId: String, assetId: String): String = "assets/$workspaceId/$assetId"

        /**
         * Validates a SHA-256 hex string.
         */
        fun isValidHash(hash: String): Boolean = hash.matches(SHA256_HASH_REGEX)
    }
}

internal val SHA256_HASH_REGEX = Regex("^[a-f0-9]{64}$")

/**
 * Workspace-scoped content-addressed blob.
 *
 * Represents the physical stored object. Keyed by (workspace_id, file_hash).
 * Multiple assets in the same workspace can reference the same blob (dedup).
 *
 * @property workspaceId The workspace that owns this blob.
 * @property fileHash SHA-256 of the file content (lowercase hex, 64 chars).
 * @property storageKey The canonical S3 key where the verified blob lives.
 *   Set only when status transitions to READY. Null for UPLOADING, FAILED,
 *   READY_FOR_GC, and GARBAGE_COLLECTED.
 * @property fileSizeBytes Size in bytes. Set only when READY.
 * @property detectedMediaType MIME type detected via magic bytes. Set only when READY.
 * @property status The blob's lifecycle status.
 * @property failureReason Human-readable reason set when status is FAILED.
 * @property orphanedAt When the blob was marked READY_FOR_GC (last active asset deleted).
 * @property gcFailureCount Number of consecutive GC failures. Caps at GC_MAX_FAILURE_COUNT.
 * @property lastGcAttemptAt Timestamp of the most recent GC attempt.
 * @property createdAt When the blob record was created.
 * @property updatedAt When the blob record was last modified.
 */
data class WorkspaceFileBlob(
    val workspaceId: String,
    val fileHash: String,
    val storageKey: String?,
    val fileSizeBytes: Long?,
    val detectedMediaType: String?,
    val status: BlobStatus,
    val failureReason: String? = null,
    val orphanedAt: Instant? = null,
    val gcFailureCount: Int = 0,
    val lastGcAttemptAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
) {
    init {
        require(workspaceId.isNotBlank()) { "Workspace ID must not be blank" }
        require(fileHash.matches(SHA256_HASH_REGEX)) {
            "fileHash must be a lowercase 64-character SHA-256 hex string"
        }

        // READY blobs must have non-null canonical metadata (enforced by DB CHECK)
        if (status == BlobStatus.READY) {
            require(storageKey != null && storageKey.isNotBlank()) {
                "READY blobs must have a non-null storageKey"
            }
            require(detectedMediaType != null) {
                "READY blobs must have a non-null detectedMediaType"
            }
            require(fileSizeBytes != null && fileSizeBytes > 0) {
                "READY blobs must have a valid fileSizeBytes"
            }
        }
    }

    companion object {
        /**
         * Generate the canonical storage key for a blob.
         */
        fun canonicalKey(workspaceId: String, fileHash: String, mediaType: String): String {
            val ext = MediaStorageKeys.parseMediaTypeExtension(mediaType)
            return "assets/$workspaceId/blobs/$fileHash$ext"
        }

        /**
         * Generate the temporary upload key for an asset.
         */
        fun tempKey(workspaceId: String, assetId: String, mediaType: String): String {
            val ext = MediaStorageKeys.parseMediaTypeExtension(mediaType)
            return "assets/$workspaceId/temp/$assetId$ext"
        }
    }
}

/**
 * Result of a blob upsert at PUT time.
 */
sealed class BlobUpsertResult {
    /**
     * The blob already existed and no new row was inserted.
     */
    data class Existed(val blob: WorkspaceFileBlob) : BlobUpsertResult()

    /**
     * A new blob row was inserted.
     */
    data class Created(val blob: WorkspaceFileBlob) : BlobUpsertResult()
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
