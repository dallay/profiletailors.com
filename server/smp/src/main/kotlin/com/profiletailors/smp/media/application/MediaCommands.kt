package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.media.domain.MediaSourceType
import reactor.core.publisher.Flux

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

/**
 * Command to transition a stale PROCESSING asset to FAILED.
 */
data class TransitionStaleAssetCommand(
    val assetId: String,
    val workspaceId: String,
)

/**
 * Exception thrown when an asset is not ready for use.
 */
class AssetNotReadyException(
    val assetId: String,
    val reason: String,
) : RuntimeException("Asset $assetId is not ready: $reason")

/**
 * Exception thrown when the media context is unavailable.
 */
class MediaServiceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

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
class UploadConflictException(
    val assetId: String,
    val currentStatus: String,
) : RuntimeException("Asset $assetId is already $currentStatus and cannot be re-uploaded.")

/**
 * Exception thrown when an upload is already in progress.
 */
class UploadInProgressException(
    val assetId: String,
    val currentStatus: String,
) : RuntimeException("Asset $assetId already has an upload in progress.")

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
class InvalidCursorException(
    message: String,
) : RuntimeException(message)

/**
 * Exception thrown when an asset is not found (cross-workspace or missing).
 */
class AssetNotFoundException(
    val assetId: String,
) : RuntimeException("Asset $assetId not found")

/**
 * Exception thrown when file size exceeds the limit during streaming.
 */
class FileTooLargeException(
    val actualSize: Long,
    val maxAllowed: Long,
) : RuntimeException("File size $actualSize exceeds max $maxAllowed")
