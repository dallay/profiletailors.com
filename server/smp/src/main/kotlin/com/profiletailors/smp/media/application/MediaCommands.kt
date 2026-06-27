package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.media.domain.MediaSourceType

/**
 * Command to create a new uploaded media asset in PROCESSING state.
 */
data class CreateUploadedAssetCommand(
    val workspaceId: String,
    val sourceType: MediaSourceType,
    val mediaType: String,
    val originalFilename: String?,
) : CommandWithResult<CreateUploadedAssetResult>

/**
 * Result of creating an uploaded asset.
 */
data class CreateUploadedAssetResult(
    val assetId: String,
    val workspaceId: String,
    val sourceType: MediaSourceType,
    val mediaType: String,
    val status: String, // "PROCESSING"
)

/**
 * Command to upload binary content to a created asset.
 *
 * @property assetId The asset to upload to.
 * @property workspaceId The workspace context.
 * @property fileStream Kotlin Flow of ByteArray chunks containing the file content.
 * @property contentLength Optional Content-Length header value for pre-check.
 * @property maxFileSizeBytes Maximum allowed file size in bytes.
 * @property contentType The declared Content-Type from the uploaded part.
 * @property timeoutSeconds Maximum duration for the upload in seconds.
 */
data class UploadAssetCommand(
    val assetId: String,
    val workspaceId: String,
    val fileStream: kotlinx.coroutines.flow.Flow<ByteArray>,
    val contentLength: Long?,
    val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    val contentType: String? = null,
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : CommandWithResult<UploadAssetResult> {
    companion object {
        private const val DEFAULT_MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024
        private const val DEFAULT_TIMEOUT_SECONDS = 10L * 60L
    }
}

/**
 * Result of uploading an asset.
 */
data class UploadAssetResult(
    val assetId: String,
    val workspaceId: String,
    val sourceType: String,
    val mediaType: String,
    val status: String, // "READY" or "FAILED"
    val originalFilename: String?,
    val fileSizeBytes: Long?,
    val createdAt: String,
)

data class DeleteWorkspaceAssetCommand(val assetId: String, val workspaceId: String) :
    CommandWithResult<DeleteWorkspaceAssetResult>

data class DeleteWorkspaceAssetResult(val assetId: String, val workspaceId: String, val deleted: Boolean)

/**
 * Command to transition a stale PROCESSING asset to FAILED.
 */
data class TransitionStaleAssetCommand(val assetId: String, val workspaceId: String)

/**
 * Exception thrown when an asset is not ready for use.
 */
class AssetNotReadyException(val assetId: String, val reason: String) :
    RuntimeException("Asset $assetId is not ready: $reason")

/**
 * Exception thrown when the media context is unavailable.
 */
class MediaServiceUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

// ── PUT Asset (CAS dedup) ──────────────────────────────────────────────

/**
 * Command to register a media asset with CAS dedup checking.
 *
 * The client generates the file hash client-side and sends it here.
 * The server checks whether a blob already exists for (workspaceId, fileHash):
 * - READY: dedup hit, return 200
 * - UPLOADING: another upload in progress, return 202
 * - FAILED/READY_FOR_GC/GARBAGE_COLLECTED: retry, reset blob to UPLOADING
 * - missing: insert both blob and asset
 */
data class PutAssetCommand(
    val assetId: String,
    val workspaceId: String,
    val fileHash: String,
    val fileSizeBytes: Long,
    val declaredMediaType: String,
    val originalFilename: String?,
) : CommandWithResult<PutAssetResult>

/**
 * Result of a PUT asset operation.
 */
sealed class PutAssetResult {
    /**
     * A new asset was created (blob did not exist before).
     * Client must upload bytes to the provided uploadUrl.
     */
    data class Created(
        val assetId: String,
        val workspaceId: String,
        val status: String,
        val mediaType: String,
        val deduped: Boolean,
        val uploadUrl: String,
        val createdAt: String,
    ) : PutAssetResult()

    /**
     * Asset already exists with the same assetId and same fileHash.
     * Idempotent — returns current state.
     */
    data class AlreadyExists(
        val assetId: String,
        val workspaceId: String,
        val status: String,
        val mediaType: String,
        val deduped: Boolean,
        val createdAt: String,
    ) : PutAssetResult()

    /**
     * Hash mismatch: assetId exists but with a different fileHash.
     */
    data class HashMismatch(val assetId: String, val existingFileHash: String) : PutAssetResult()

    /**
     * Blob is currently being uploaded by another request.
     * Client should poll after retryAfterSeconds.
     */
    data class WaitingForBlob(val assetId: String, val retryAfterSeconds: Int) : PutAssetResult()
}

// ── Legacy Upload (multipart, kept for backward compatibility) ─────────────────

/**
 * Legacy command to upload binary content to a created asset.
 * @deprecated Use CAS upload flow (PUT + POST /upload with octet-stream) instead.
 */
data class LegacyUploadAssetCommand(
    val assetId: String,
    val workspaceId: String,
    val fileStream: kotlinx.coroutines.flow.Flow<ByteArray>,
    val contentLength: Long?,
    val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    val contentType: String? = null,
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : CommandWithResult<LegacyUploadAssetResult> {
    companion object {
        private const val DEFAULT_MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024
        private const val DEFAULT_TIMEOUT_SECONDS = 10L * 60L
    }
}

/**
 * Result of a legacy asset upload.
 * @deprecated Use CAS upload result instead.
 */
data class LegacyUploadAssetResult(
    val assetId: String,
    val workspaceId: String,
    val sourceType: String,
    val mediaType: String,
    val status: String,
    val originalFilename: String?,
    val fileSizeBytes: Long?,
    val createdAt: String,
)

// ── Upload Asset (streaming, CAS) ────────────────────────────────────────

/**
 * Command to upload binary content to a CAS media asset.
 *
 * Streams bytes to temp storage, computes SHA-256, validates magic bytes,
 * then copies to canonical key on success.
 */
data class CasUploadAssetCommand(
    val assetId: String,
    val workspaceId: String,
    val fileStream: kotlinx.coroutines.flow.Flow<ByteArray>,
    val declaredFileHash: String,
    val declaredFileSizeBytes: Long,
    val declaredMediaType: String,
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : CommandWithResult<CasUploadAssetResult> {
    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 10L * 60L
    }
}

/**
 * Result of a CAS upload asset operation.
 */
sealed class CasUploadAssetResult {
    data class Ready(
        val assetId: String,
        val workspaceId: String,
        val status: String,
        val mediaType: String,
        val detectedMediaType: String,
        val deduped: Boolean,
        val fileSizeBytes: Long,
        val createdAt: String,
    ) : CasUploadAssetResult()

    data class UploadInProgress(val assetId: String) : CasUploadAssetResult()

    data class NotFound(val assetId: String) : CasUploadAssetResult()
}

// ── Delete Asset (CAS soft-delete) ──────────────────────────────────────

/**
 * Command to soft-delete a media asset and potentially schedule its blob for GC.
 */
data class DeleteAssetCommand(val assetId: String, val workspaceId: String) : CommandWithResult<DeleteAssetResult>

/**
 * Result of a delete asset operation.
 */
data class DeleteAssetResult(val deleted: Boolean, val blobScheduledForGC: Boolean)

data class MediaUploadSettings(
    val maxConcurrentUploads: Int,
    val maxCreationsPerHour: Int,
    val storageBucket: String,
) {
    init {
        require(maxConcurrentUploads > 0) {
            "media.max-concurrent-uploads must be greater than zero"
        }
        require(maxCreationsPerHour > 0) {
            "media.max-creations-per-hour must be greater than zero"
        }
        require(storageBucket.isNotBlank()) {
            "media.storage.bucket must not be blank"
        }
    }
}

data class MediaReconcilerSettings(
    val storageBucket: String,
    val staleThresholdHours: Long,
    val gracePeriodMinutes: Long,
) {
    init {
        require(storageBucket.isNotBlank()) {
            "media.storage.bucket must not be blank"
        }
        require(staleThresholdHours > 0) {
            "media.stale.threshold-hours must be greater than zero"
        }
        require(gracePeriodMinutes > 0) {
            "media.stale.grace-period-minutes must be greater than zero"
        }
    }
}

/**
 * Exception thrown when media type validation fails.
 */
class UnsupportedMediaTypeException(
    message: String,
    val declaredType: String? = null,
    val detectedType: String? = null,
) : RuntimeException(message)

/**
 * Exception thrown when upload conflict is detected.
 */
class UploadConflictException(val assetId: String, val currentStatus: String) :
    RuntimeException("Asset $assetId is already $currentStatus and cannot be re-uploaded.")

/**
 * Exception thrown when an upload is already in progress.
 */
class UploadInProgressException(val assetId: String, val currentStatus: String) :
    RuntimeException("Asset $assetId already has an upload in progress.")

/**
 * Exception thrown when a rate limit is exceeded.
 */
class RateLimitExceededException(
    val workspaceId: String,
    val limitType: String, // "concurrent_uploads" | "hourly_creations"
    val currentValue: Int,
    val limitValue: Int,
    val retryAfterSeconds: Int,
) : RuntimeException("Rate limit exceeded: $limitType ($currentValue/$limitValue)")

/**
 * Exception thrown when a pagination cursor is invalid.
 */
class InvalidCursorException(message: String) : RuntimeException(message)

/**
 * Exception thrown when an asset is not found (cross-workspace or missing).
 */
class AssetNotFoundException(val assetId: String) : RuntimeException("Asset $assetId not found")

/**
 * Exception thrown when file size exceeds the limit during streaming.
 */
class FileTooLargeException(val actualSize: Long, val maxAllowed: Long) :
    RuntimeException("File size $actualSize exceeds max $maxAllowed")
