package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.StorageException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class CreateUploadedAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    @Value("\${media.max-concurrent-uploads:5}") private val maxConcurrentUploads: Int = 5,
    @Value("\${media.max-creations-per-hour:200}") private val maxCreationsPerHour: Int = 200,
) : CommandWithResultHandler<CreateUploadedAssetCommand, CreateUploadedAssetResult> {

    private val logger = LoggerFactory.getLogger(CreateUploadedAssetHandler::class.java)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
        private const val HOURLY_CREATIONS_RETRY_AFTER_SECONDS = 3_600
        private val WORD_EXTENSIONS = setOf("doc", "docx")
        private val POWERPOINT_EXTENSIONS = setOf("ppt", "pptx")
    }

    override suspend fun handle(command: CreateUploadedAssetCommand): CreateUploadedAssetResult {
        enforceCreationRateLimit(command.workspaceId)
        validateCreateCommand(command)

        val assetId = MediaAsset.generateAssetId()
        val storageKey = MediaAsset.generateStorageKey(command.workspaceId, assetId)
        val now = Instant.now()

        val asset = MediaAsset(
            assetId = assetId,
            workspaceId = command.workspaceId,
            sourceType = command.sourceType,
            mediaType = command.mediaType,
            storageKey = storageKey,
            originalFilename = command.originalFilename,
            status = MediaAssetStatus.PROCESSING,
            createdAt = now,
        )

        mediaAssetRepository.create(asset)
        logger.info(
            "media.asset.reserved assetId=$assetId workspaceId=${command.workspaceId} " +
                "mediaType=${command.mediaType} sourceType=${command.sourceType}",
        )

        return CreateUploadedAssetResult(
            assetId = assetId,
            workspaceId = command.workspaceId,
            sourceType = command.sourceType,
            mediaType = command.mediaType,
            status = MediaAssetStatus.PROCESSING.name,
        )
    }

    private suspend fun enforceCreationRateLimit(workspaceId: String) {
        val rateLimitOk = mediaRateLimitRepository.tryIncrementHourlyCreationCount(
            workspaceId,
            maxCreationsPerHour,
        )
        if (!rateLimitOk) {
            throw RateLimitExceededException(
                workspaceId = workspaceId,
                limitType = "hourly_creations",
                currentValue = maxCreationsPerHour,
                limitValue = maxCreationsPerHour,
                retryAfterSeconds = HOURLY_CREATIONS_RETRY_AFTER_SECONDS,
            )
        }
    }

    private fun validateCreateCommand(command: CreateUploadedAssetCommand) {
        validateSupportedMediaType(command.mediaType)
        validateOriginalFilenameForOoxml(command.mediaType, command.originalFilename)
        validateSourceType(command.sourceType)
    }

    private fun validateSupportedMediaType(mediaType: String) {
        if (mediaType !in MediaAsset.SUPPORTED_MEDIA_TYPES) {
            val supportedTypes = MediaAsset.SUPPORTED_MEDIA_TYPES.joinToString()
            throw UnsupportedMediaTypeException(
                "Unsupported media type: $mediaType. Supported types: $supportedTypes",
                declaredType = mediaType,
            )
        }
    }

    private fun validateOriginalFilenameForOoxml(mediaType: String, originalFilename: String?) {
        if (mediaType !in MediaAsset.OOXML_MEDIA_TYPES) {
            return
        }

        val filename = originalFilename
        if (filename.isNullOrBlank()) {
            throw UnsupportedMediaTypeException(
                "originalFilename is required for OOXML formats",
                declaredType = mediaType,
            )
        }

        val extension = filename.substringAfterLast('.', "").lowercase()
        val validExtensions = when (mediaType) {
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                WORD_EXTENSIONS
            }
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> {
                POWERPOINT_EXTENSIONS
            }
            else -> emptySet()
        }

        if (extension !in validExtensions) {
            throw UnsupportedMediaTypeException(
                "Invalid file extension for OOXML format: $extension",
                declaredType = mediaType,
            )
        }
    }

    private fun validateSourceType(sourceType: MediaSourceType) {
        if (sourceType != MediaSourceType.UPLOADED) {
            throw UnsupportedMediaTypeException(
                "Unsupported source type: $sourceType. Only UPLOADED is supported.",
                declaredType = sourceType.name,
            )
        }
    }
}

@Suppress("TooManyFunctions")
@Service
class UploadAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val storageApplicationService: StorageApplicationService,
    @Value("\${media.max-concurrent-uploads:5}") private val maxConcurrentUploads: Int = 5,
    @Value("\${media.storage.bucket:attachments}") private val storageBucket: String = "attachments",
) : CommandWithResultHandler<UploadAssetCommand, UploadAssetResult> {

    private val logger = LoggerFactory.getLogger(UploadAssetHandler::class.java)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
        private const val CONCURRENT_UPLOADS_RETRY_AFTER_SECONDS = 300
        private const val CLEANUP_TIMEOUT_MILLIS = 30_000L
        private const val JPEG_SIGNATURE_SIZE = 3
        private const val PNG_SIGNATURE_SIZE = 4
        private const val GIF_SIGNATURE_SIZE = 4
        private const val WEBP_SIGNATURE_SIZE = 12
        private const val MP4_SIGNATURE_SIZE = 8
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        private val GIF_MAGIC = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte())
        private val RIFF_MAGIC = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
        private val WEBP_MAGIC = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())
        private val MP4_MAGIC = byteArrayOf(0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte())
        private val OOXML_MAGIC = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
        private const val RIFF_OFFSET = 8
        private const val MP4_OFFSET = 4
        private const val MILLIS_PER_SECOND = 1_000L
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun handle(command: UploadAssetCommand): UploadAssetResult {
        val now = Instant.now()
        val assetId = command.assetId
        val workspaceId = command.workspaceId

        claimConcurrentUploadSlot(workspaceId)

        try {
            val asset = requireUploadableAsset(workspaceId, assetId, now)

            logger.info(
                "media.asset.upload.started assetId=$assetId workspaceId=$workspaceId " +
                    "contentLength=${command.contentLength}",
            )

            val startTime = System.currentTimeMillis()
            val fileSize = uploadWithStreamingValidation(command, asset, storageBucket)

            val updated = mediaAssetRepository.markAsReady(assetId, workspaceId, fileSize)
                ?: throw IllegalStateException("Asset not found after upload: $assetId")

            val durationMs = System.currentTimeMillis() - startTime
            logger.info(
                "media.asset.upload.completed assetId=$assetId workspaceId=$workspaceId " +
                    "fileSizeBytes=$fileSize durationMs=$durationMs",
            )

            return UploadAssetResult(
                assetId = assetId,
                workspaceId = workspaceId,
                sourceType = updated.sourceType.name,
                mediaType = updated.mediaType,
                status = updated.status.name,
                originalFilename = updated.originalFilename,
                fileSizeBytes = updated.fileSizeBytes,
                createdAt = ISO_FORMATTER.format(updated.createdAt.atOffset(ZoneOffset.UTC)),
            )
        } catch (e: RuntimeException) {
            handleUploadFailure(e, assetId, workspaceId)
            throw e
        } finally {
            mediaRateLimitRepository.releaseConcurrentUploadSlot(workspaceId)
        }
    }

    private suspend fun claimConcurrentUploadSlot(workspaceId: String) {
        val slotClaimed = mediaRateLimitRepository.tryClaimConcurrentUploadSlot(
            workspaceId,
            maxConcurrentUploads,
        )
        if (!slotClaimed) {
            throw RateLimitExceededException(
                workspaceId = workspaceId,
                limitType = "concurrent_uploads",
                currentValue = maxConcurrentUploads,
                limitValue = maxConcurrentUploads,
                retryAfterSeconds = CONCURRENT_UPLOADS_RETRY_AFTER_SECONDS,
            )
        }
    }

    private suspend fun requireUploadableAsset(
        workspaceId: String,
        assetId: String,
        now: Instant,
    ): MediaAsset {
        val asset = mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
            ?: throw AssetNotFoundException(assetId)
        validateAssetIsNotReady(assetId, asset)
        verifyUploadSlotCanBeClaimed(assetId, workspaceId, now)
        return asset
    }

    private fun validateAssetIsNotReady(assetId: String, asset: MediaAsset) {
        if (asset.status == MediaAssetStatus.READY) {
            throw UploadConflictException(assetId, asset.status.name)
        }
    }

    private suspend fun verifyUploadSlotCanBeClaimed(
        assetId: String,
        workspaceId: String,
        now: Instant,
    ) {
        val claimed = mediaAssetRepository.claimUploadSlot(assetId, workspaceId, now)
        if (!claimed) {
            throw resolveUploadConflict(assetId, workspaceId)
        }
    }

    private suspend fun resolveUploadConflict(
        assetId: String,
        workspaceId: String,
    ): RuntimeException {
        val reloaded = mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
        return if (reloaded?.status == MediaAssetStatus.READY) {
            UploadConflictException(assetId, MediaAssetStatus.READY.name)
        } else {
            UploadInProgressException(assetId, reloaded?.status?.name ?: "UNKNOWN")
        }
    }

    private suspend fun handleUploadFailure(e: Exception, assetId: String, workspaceId: String) {
        val storageWriteAttempted = didStorageWriteStart(e)
        val cleanupSucceeded = cleanupPartialStorageIfNeeded(storageWriteAttempted, assetId, workspaceId)
        markAssetFailed(assetId, workspaceId)
        val reason = uploadFailureReason(e)

        logger.info(
            "media.asset.upload.failed assetId=$assetId workspaceId=$workspaceId reason=$reason " +
                "storageWriteAttempted=$storageWriteAttempted storageCleanupSucceeded=$cleanupSucceeded",
        )
    }

    private fun didStorageWriteStart(error: Exception): Boolean {
        return error !is UnsupportedMediaTypeException &&
            error !is FileTooLargeException &&
            error !is RateLimitExceededException &&
            error !is AssetNotFoundException &&
            error !is UploadConflictException &&
            error !is UploadInProgressException
    }

    private suspend fun cleanupPartialStorageIfNeeded(
        storageWriteAttempted: Boolean,
        assetId: String,
        workspaceId: String,
    ): Boolean? {
        val asset = tryLoadAssetForCleanup(assetId, workspaceId)
        if (!storageWriteAttempted || asset == null || asset.storageKey.isBlank()) {
            return null
        }
        return deletePartialStorageObject(assetId, asset.storageKey)
    }

    private suspend fun tryLoadAssetForCleanup(assetId: String, workspaceId: String): MediaAsset? {
        return try {
            mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
        } catch (_: IllegalStateException) {
            null
        }
    }

    private suspend fun deletePartialStorageObject(assetId: String, storageKey: String): Boolean {
        return try {
            withTimeout(CLEANUP_TIMEOUT_MILLIS) {
                storageApplicationService.delete(
                    storageBucket,
                    storageKey,
                    "media-reconciler",
                )
            }
            logger.info(
                "media.asset.cleanup.attempted assetId=$assetId storageKey=$storageKey success=true",
            )
            true
        } catch (cleanupError: StorageException) {
            logCleanupFailure(assetId, storageKey, cleanupError)
            false
        } catch (cleanupError: TimeoutCancellationException) {
            logCleanupFailure(assetId, storageKey, cleanupError)
            false
        }
    }

    private fun logCleanupFailure(assetId: String, storageKey: String, cleanupError: Throwable) {
        logger.warn(
            "media.asset.cleanup.attempted assetId=$assetId storageKey=$storageKey " +
                "success=false error=${cleanupError.message}",
            cleanupError,
        )
    }

    private suspend fun markAssetFailed(assetId: String, workspaceId: String) {
        try {
            mediaAssetRepository.markAsFailed(assetId, workspaceId)
        } catch (transitionError: IllegalStateException) {
            logger.error("Failed to transition asset to FAILED: assetId=$assetId", transitionError)
        }
    }

    private fun uploadFailureReason(error: Exception): String {
        return when (error) {
            is AssetNotFoundException -> "asset not found"
            is UploadConflictException -> "asset already ready"
            is UploadInProgressException -> "upload already in progress"
            is UnsupportedMediaTypeException -> "unsupported media type"
            is FileTooLargeException -> "file too large"
            is RateLimitExceededException -> "rate limit exceeded"
            is TimeoutCancellationException -> "upload timeout"
            else -> "storage error: ${error.message}"
        }
    }

    private suspend fun uploadWithStreamingValidation(
        command: UploadAssetCommand,
        asset: MediaAsset,
        bucket: String,
    ): Long {
        val maxSize = command.maxFileSizeBytes
        var bytesReceived = 0L
        var validatedMagicBytes = false

        // Collect bytes in small chunks, validating magic bytes
        val validatedContent = mutableListOf<ByteArray>()

        command.fileStream.collect { bytes ->
            // Magic-byte validation: check first few bytes on first chunk
            if (!validatedMagicBytes && bytesReceived == 0L) {
                val detectedType = detectMediaType(bytes, command.contentType)
                if (detectedType != null && detectedType != asset.mediaType) {
                    throw UnsupportedMediaTypeException(
                        "Magic-byte validation failed: detected $detectedType but declared " +
                            asset.mediaType,
                        declaredType = asset.mediaType,
                        detectedType = detectedType,
                    )
                }
                validatedMagicBytes = true
            }

            // Size check
            val newTotal = bytesReceived + bytes.size
            if (newTotal > maxSize) {
                throw FileTooLargeException(newTotal, maxSize)
            }

            bytesReceived = newTotal
            validatedContent.add(bytes)
        }

        // Stream to storage
        val uploadFlow = kotlinx.coroutines.flow.flow {
            for (chunk in validatedContent) {
                emit(chunk)
            }
        }

        withTimeout(command.timeoutSeconds * MILLIS_PER_SECOND) {
            storageApplicationService.upload(
                bucket = bucket,
                key = asset.storageKey,
                content = uploadFlow,
                uploaderId = command.workspaceId,
                metadata = mapOf(
                    "assetId" to asset.assetId,
                    "workspaceId" to asset.workspaceId,
                    "contentType" to asset.mediaType,
                ),
            )
        }

        return bytesReceived
    }

    private fun detectMediaType(headerBytes: ByteArray, declaredType: String?): String? {
        val sig = headerBytes.take(12).toByteArray()

        return when {
            hasPrefix(sig, JPEG_MAGIC, JPEG_SIGNATURE_SIZE) -> "image/jpeg"
            hasPrefix(sig, PNG_MAGIC, PNG_SIGNATURE_SIZE) -> "image/png"
            hasPrefix(sig, GIF_MAGIC, GIF_SIGNATURE_SIZE) -> "image/gif"
            hasOffsetPrefix(sig, RIFF_MAGIC, 0) && hasOffsetPrefix(sig, WEBP_MAGIC, RIFF_OFFSET) -> {
                "image/webp"
            }
            hasOffsetPrefix(sig, MP4_MAGIC, MP4_OFFSET) -> "video/mp4"
            hasPrefix(sig, OOXML_MAGIC, PNG_SIGNATURE_SIZE) -> declaredType
            else -> null
        }
    }

    private fun hasPrefix(signature: ByteArray, magic: ByteArray, requiredSize: Int): Boolean {
        return signature.size >= requiredSize &&
            magic.indices.all { index -> signature[index] == magic[index] }
    }

    private fun hasOffsetPrefix(signature: ByteArray, magic: ByteArray, offset: Int): Boolean {
        val requiredSize = offset + magic.size
        return signature.size >= requiredSize &&
            magic.indices.all { index -> signature[offset + index] == magic[index] }
    }
}

@Service
class ListWorkspaceAssetsHandler(
    private val mediaAssetRepository: MediaAssetRepository,
) : QueryHandler<ListWorkspaceAssetsQuery, ListWorkspaceAssetsResult> {

    private val logger = LoggerFactory.getLogger(ListWorkspaceAssetsHandler::class.java)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }

    override suspend fun handle(query: ListWorkspaceAssetsQuery): ListWorkspaceAssetsResult {
        val result = mediaAssetRepository.listByWorkspace(
            workspaceId = query.workspaceId,
            statuses = query.statuses,
            pageSize = query.pageSize,
            cursor = query.cursor,
        )

        return ListWorkspaceAssetsResult(
            assets = result.assets.map { asset ->
                MediaAssetSummary(
                    assetId = asset.assetId,
                    workspaceId = asset.workspaceId,
                    mediaType = asset.mediaType,
                    sourceType = asset.sourceType.name,
                    status = asset.status.name,
                    originalFilename = asset.originalFilename,
                    fileSizeBytes = asset.fileSizeBytes,
                    createdAt = ISO_FORMATTER.format(asset.createdAt.atOffset(ZoneOffset.UTC)),
                )
            },
            nextCursor = result.nextCursor,
        )
    }
}

@Service
class GetWorkspaceAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
) : QueryHandler<GetWorkspaceAssetQuery, MediaAssetSummary> {

    private val logger = LoggerFactory.getLogger(GetWorkspaceAssetHandler::class.java)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }

    override suspend fun handle(query: GetWorkspaceAssetQuery): MediaAssetSummary {
        val asset = mediaAssetRepository.findByWorkspaceAndId(query.workspaceId, query.assetId)
            ?: throw AssetNotFoundException(query.assetId)

        return MediaAssetSummary(
            assetId = asset.assetId,
            workspaceId = asset.workspaceId,
            mediaType = asset.mediaType,
            sourceType = asset.sourceType.name,
            status = asset.status.name,
            originalFilename = asset.originalFilename,
            fileSizeBytes = asset.fileSizeBytes,
            createdAt = ISO_FORMATTER.format(asset.createdAt.atOffset(ZoneOffset.UTC)),
        )
    }
}
