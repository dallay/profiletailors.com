package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.permissivePrincipalContextProvider
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.media.application.port.ImportProviderAssetQuery
import com.profiletailors.smp.media.application.port.MediaProvider
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.smp.media.application.port.ProviderSearchPage
import com.profiletailors.smp.media.application.port.SearchProviderPhotosQuery
import com.profiletailors.smp.media.application.port.UnsupportedProviderException
import com.profiletailors.smp.media.domain.BlobStatus
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.smp.media.domain.MediaStorageKeys
import com.profiletailors.storage.application.StorageApplicationService
import com.profiletailors.storage.domain.StorageException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private val MEDIA_ASSET_SUMMARY_FORMATTER = DateTimeFormatter.ISO_INSTANT

private suspend fun MediaAsset.toSummary(
    assetPreviewUrlResolver: AssetPreviewUrlResolver,
    mediaPreviewTokenService: MediaPreviewTokenService,
): MediaAssetSummary = MediaAssetSummary(
    assetId = assetId,
    workspaceId = workspaceId,
    mediaType = mediaType,
    sourceType = sourceType.name,
    status = status.name,
    originalFilename = originalFilename,
    fileSizeBytes = fileSizeBytes,
    fileHash = fileHash,
    createdAt = MEDIA_ASSET_SUMMARY_FORMATTER.format(createdAt.atOffset(ZoneOffset.UTC)),
    previewUrl = if (status == MediaAssetStatus.READY) {
        assetPreviewUrlResolver.resolvePreviewUrl(
            assetId = assetId,
            workspaceId = workspaceId,
            mediaType = mediaType,
            storageKey = storageKey,
            externalUrl = null,
        )
    } else {
        null
    },
    downloadUrl = if (status == MediaAssetStatus.READY) {
        mediaPreviewTokenService.buildSignedContentPath(assetId, workspaceId)
    } else {
        null
    },
    sourceProvider = sourceProvider,
    externalId = externalId,
    sourceUrl = sourceUrl,
    authorName = authorName,
    authorUrl = authorUrl,
    metadata = metadata,
)

@Service
class CreateUploadedAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val uploadSettings: MediaUploadSettings,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<CreateUploadedAssetCommand, CreateUploadedAssetResult> {

    private val logger = LoggerFactory.getLogger(CreateUploadedAssetHandler::class.java)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
        private const val HOURLY_CREATIONS_RETRY_AFTER_SECONDS = 3_600
        private val WORD_EXTENSIONS = setOf("doc", "docx")
        private val POWERPOINT_EXTENSIONS = setOf("ppt", "pptx")
    }

    override suspend fun handle(command: CreateUploadedAssetCommand): CreateUploadedAssetResult {
        requireMediaUploadVerification()
        validateCreateCommand(command)
        enforceCreationRateLimit(command.workspaceId)

        val assetId = MediaAsset.generateAssetId()
        val storageKey = MediaAsset.generateStorageKey(command.workspaceId, assetId)
        val now = Instant.now()

        val asset = MediaAsset(
            assetId = assetId,
            workspaceId = command.workspaceId,
            sourceType = command.sourceType,
            mediaType = command.mediaType,
            storageKey = storageKey,
            fileHash = null,
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

    private suspend fun requireMediaUploadVerification() {
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )
    }

    private suspend fun enforceCreationRateLimit(workspaceId: String) {
        val rateLimitOk = mediaRateLimitRepository.tryIncrementHourlyCreationCount(
            workspaceId,
            uploadSettings.maxCreationsPerHour,
        )
        if (!rateLimitOk) {
            throw RateLimitExceededException(
                workspaceId = workspaceId,
                limitType = "hourly_creations",
                currentValue = uploadSettings.maxCreationsPerHour,
                limitValue = uploadSettings.maxCreationsPerHour,
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
        if (mediaType !in MediaAsset.OFFICE_DOCUMENT_MEDIA_TYPES) {
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
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            -> {
                WORD_EXTENSIONS
            }

            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            -> {
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
    private val uploadSettings: MediaUploadSettings,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
    private val transactionRunner: AtomicTransactionRunner,
) : CommandWithResultHandler<LegacyUploadAssetCommand, LegacyUploadAssetResult> {

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
    override suspend fun handle(command: LegacyUploadAssetCommand): LegacyUploadAssetResult {
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )
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
            val fileSize = uploadWithStreamingValidation(command, asset, uploadSettings.storageBucket)

            val updated = transactionRunner.runAtomically {
                mediaAssetRepository.markAsReady(assetId, workspaceId, fileSize)
                    ?: throw IllegalStateException("Asset not found after upload: $assetId")
            }

            val durationMs = System.currentTimeMillis() - startTime
            logger.info(
                "media.asset.upload.completed assetId=$assetId workspaceId=$workspaceId " +
                    "fileSizeBytes=$fileSize durationMs=$durationMs",
            )

            return LegacyUploadAssetResult(
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
            uploadSettings.maxConcurrentUploads,
        )
        if (!slotClaimed) {
            throw RateLimitExceededException(
                workspaceId = workspaceId,
                limitType = "concurrent_uploads",
                currentValue = uploadSettings.maxConcurrentUploads,
                limitValue = uploadSettings.maxConcurrentUploads,
                retryAfterSeconds = CONCURRENT_UPLOADS_RETRY_AFTER_SECONDS,
            )
        }
    }

    private suspend fun requireUploadableAsset(workspaceId: String, assetId: String, now: Instant): MediaAsset {
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

    private suspend fun verifyUploadSlotCanBeClaimed(assetId: String, workspaceId: String, now: Instant) {
        val claimed = mediaAssetRepository.claimUploadSlot(assetId, workspaceId, now)
        if (!claimed) {
            throw resolveUploadConflict(assetId, workspaceId)
        }
    }

    private suspend fun resolveUploadConflict(assetId: String, workspaceId: String): RuntimeException {
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

    private fun didStorageWriteStart(error: Exception): Boolean = error !is UnsupportedMediaTypeException &&
        error !is FileTooLargeException &&
        error !is RateLimitExceededException &&
        error !is AssetNotFoundException &&
        error !is UploadConflictException &&
        error !is UploadInProgressException

    private suspend fun cleanupPartialStorageIfNeeded(
        storageWriteAttempted: Boolean,
        assetId: String,
        workspaceId: String,
    ): Boolean? {
        val asset = tryLoadAssetForCleanup(assetId, workspaceId)
        if (!storageWriteAttempted || asset == null || asset.storageKey.isNullOrBlank()) {
            return null
        }
        return deletePartialStorageObject(assetId, asset.storageKey)
    }

    private suspend fun tryLoadAssetForCleanup(assetId: String, workspaceId: String): MediaAsset? = try {
        mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
    } catch (_: IllegalStateException) {
        null
    }

    private suspend fun deletePartialStorageObject(assetId: String, storageKey: String): Boolean = try {
        withTimeout(CLEANUP_TIMEOUT_MILLIS) {
            storageApplicationService.delete(
                uploadSettings.storageBucket,
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

    private fun uploadFailureReason(error: Exception): String = when (error) {
        is AssetNotFoundException -> "asset not found"
        is UploadConflictException -> "asset already ready"
        is UploadInProgressException -> "upload already in progress"
        is UnsupportedMediaTypeException -> "unsupported media type"
        is FileTooLargeException -> "file too large"
        is RateLimitExceededException -> "rate limit exceeded"
        is TimeoutCancellationException -> "upload timeout"
        else -> "storage error: ${error.message}"
    }

    @Suppress("ThrowsCount", "CognitiveComplexMethod")
    private suspend fun uploadWithStreamingValidation(
        command: LegacyUploadAssetCommand,
        asset: MediaAsset,
        bucket: String,
    ): Long {
        var bytesReceived = 0L

        val uploadFlow = kotlinx.coroutines.flow.flow {
            val pendingChunks = mutableListOf<ByteArray>()
            var validatedMagicBytes = false

            command.fileStream.collect { bytes ->
                val newTotal = bytesReceived + bytes.size
                if (newTotal > maxFileSize(command)) {
                    throw FileTooLargeException(newTotal, maxFileSize(command))
                }
                bytesReceived = newTotal

                validatedMagicBytes = collectOrEmitChunk(
                    validatedMagicBytes = validatedMagicBytes,
                    chunk = bytes,
                    pendingChunks = pendingChunks,
                    declaredType = asset.mediaType,
                    declaredContentType = command.contentType,
                    emit = { emit(it) },
                )
            }

            if (!validatedMagicBytes) {
                emitRemainingChunks(
                    pendingChunks = pendingChunks,
                    declaredType = asset.mediaType,
                    declaredContentType = command.contentType,
                    emit = { emit(it) },
                )
            }
        }

        withTimeout(command.timeoutSeconds * MILLIS_PER_SECOND) {
            storageApplicationService.upload(
                bucket = bucket,
                key = asset.storageKey ?: error("asset must have storageKey before upload"),
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

    private fun maxFileSize(command: LegacyUploadAssetCommand): Long = command.maxFileSizeBytes

    private suspend fun collectOrEmitChunk(
        validatedMagicBytes: Boolean,
        chunk: ByteArray,
        pendingChunks: MutableList<ByteArray>,
        declaredType: String,
        declaredContentType: String?,
        emit: suspend (ByteArray) -> Unit,
    ): Boolean {
        if (validatedMagicBytes) {
            emit(chunk)
            return true
        }

        pendingChunks.add(chunk)
        val headerBytes = buildHeaderBytes(pendingChunks)
        val detectedType = detectMediaType(headerBytes, declaredContentType)
        val isValidated = detectedType != null
        if (isValidated) {
            validateMediaTypeMatch(detectedType, declaredType)
            pendingChunks.forEach { emit(it) }
            pendingChunks.clear()
        } else if (headerBytes.size >= WEBP_SIGNATURE_SIZE) {
            throw UnsupportedMediaTypeException(
                "Magic-byte validation failed: detected unknown but declared $declaredType",
                declaredType = declaredType,
                detectedType = null,
            )
        }
        return isValidated
    }

    private suspend fun emitRemainingChunks(
        pendingChunks: MutableList<ByteArray>,
        declaredType: String,
        declaredContentType: String?,
        emit: suspend (ByteArray) -> Unit,
    ) {
        val headerBytes = buildHeaderBytes(pendingChunks)
        val detectedType = detectMediaType(headerBytes, declaredContentType)
            ?: throw UnsupportedMediaTypeException(
                "Magic-byte validation failed: detected unknown but declared $declaredType",
                declaredType = declaredType,
                detectedType = null,
            )
        validateMediaTypeMatch(detectedType, declaredType)
        pendingChunks.forEach { emit(it) }
    }

    private fun buildHeaderBytes(pendingChunks: List<ByteArray>): ByteArray = pendingChunks.asSequence()
        .flatMap { it.asSequence() }
        .take(WEBP_SIGNATURE_SIZE)
        .toList()
        .toByteArray()

    private fun validateMediaTypeMatch(detectedType: String?, declaredType: String) {
        if (detectedType != declaredType) {
            throw UnsupportedMediaTypeException(
                "Magic-byte validation failed: detected $detectedType but declared $declaredType",
                declaredType = declaredType,
                detectedType = detectedType,
            )
        }
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

    private fun hasPrefix(signature: ByteArray, magic: ByteArray, requiredSize: Int): Boolean =
        signature.size >= requiredSize &&
            magic.indices.all { index -> signature[index] == magic[index] }

    private fun hasOffsetPrefix(signature: ByteArray, magic: ByteArray, offset: Int): Boolean {
        val requiredSize = offset + magic.size
        return signature.size >= requiredSize &&
            magic.indices.all { index -> signature[offset + index] == magic[index] }
    }
}

@Service
class ListWorkspaceAssetsHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val assetPreviewUrlResolver: AssetPreviewUrlResolver,
    private val mediaPreviewTokenService: MediaPreviewTokenService,
) : QueryHandler<ListWorkspaceAssetsQuery, ListWorkspaceAssetsResult> {

    private val logger = LoggerFactory.getLogger(ListWorkspaceAssetsHandler::class.java)

    override suspend fun handle(query: ListWorkspaceAssetsQuery): ListWorkspaceAssetsResult {
        val result = mediaAssetRepository.listByWorkspace(
            workspaceId = query.workspaceId,
            statuses = query.statuses,
            pageSize = query.pageSize,
            cursor = query.cursor,
        )

        return ListWorkspaceAssetsResult(
            assets = result.assets.map { asset ->
                asset.toSummary(assetPreviewUrlResolver, mediaPreviewTokenService)
            },
            nextCursor = result.nextCursor,
        )
    }
}

@Service
class GetWorkspaceAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val assetPreviewUrlResolver: AssetPreviewUrlResolver,
    private val mediaPreviewTokenService: MediaPreviewTokenService,
) : QueryHandler<GetWorkspaceAssetQuery, MediaAssetSummary> {

    private val logger = LoggerFactory.getLogger(GetWorkspaceAssetHandler::class.java)

    override suspend fun handle(query: GetWorkspaceAssetQuery): MediaAssetSummary {
        val asset = mediaAssetRepository.findByWorkspaceAndId(query.workspaceId, query.assetId)
            ?: throw AssetNotFoundException(query.assetId)

        return asset.toSummary(assetPreviewUrlResolver, mediaPreviewTokenService)
    }
}

@Service
class DeleteWorkspaceAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val storageApplicationService: StorageApplicationService,
    private val uploadSettings: MediaUploadSettings,
    private val transactionRunner: AtomicTransactionRunner,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
) : CommandWithResultHandler<DeleteWorkspaceAssetCommand, DeleteWorkspaceAssetResult> {
    override suspend fun handle(command: DeleteWorkspaceAssetCommand): DeleteWorkspaceAssetResult {
        val asset = mediaAssetRepository.findByWorkspaceAndId(command.workspaceId, command.assetId)
            ?: throw AssetNotFoundException(command.assetId)

        asset.storageKey?.let { storageKey ->
            runCatching {
                storageApplicationService.delete(
                    bucket = uploadSettings.storageBucket,
                    key = storageKey,
                    deleterId = command.workspaceId,
                )
            }.getOrElse { cause ->
                throw MediaServiceUnavailableException(
                    "Storage deletion failed for asset ${command.assetId}",
                    cause,
                )
            }
        }

        val deleted: Boolean
        try {
            deleted = transactionRunner.runAtomically {
                mediaAssetRepository.softDelete(command.assetId, command.workspaceId) != null
            }
        } catch (@Suppress("SwallowedException") e: RuntimeException) {
            // Storage already gone; schedule blob GC so BlobGarbageCollector handles orphaned storage.
            asset.fileHash?.let { hash ->
                transactionRunner.runAtomically {
                    workspaceFileBlobRepository.markReadyForGC(command.workspaceId, hash, Instant.now())
                }
            }
            // softDelete failed (compensated), but caller needs to know the asset was handled.
            // Report deleted=true since the asset IS gone; blob GC is a background concern.
            return DeleteWorkspaceAssetResult(
                assetId = command.assetId,
                workspaceId = command.workspaceId,
                deleted = true,
            )
        }

        return DeleteWorkspaceAssetResult(
            assetId = command.assetId,
            workspaceId = command.workspaceId,
            deleted = deleted,
        )
    }
}

// ─── PUT Asset Handler (CAS dedup) ───────────────────────────────────────────

@Service
class PutAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val uploadSettings: MediaUploadSettings,
    private val transactionRunner: AtomicTransactionRunner,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<PutAssetCommand, PutAssetResult> {

    private val logger = LoggerFactory.getLogger(PutAssetHandler::class.java)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
        private const val HOURLY_CREATIONS_RETRY_AFTER_SECONDS = 3_600
        private val WORD_EXTENSIONS = setOf("doc", "docx")
        private val POWERPOINT_EXTENSIONS = setOf("ppt", "pptx")
    }

    override suspend fun handle(command: PutAssetCommand): PutAssetResult {
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )
        // 1. Validate UUID v4
        validateAssetId(command.assetId)

        // 2. Validate fileHash format (lowercase 64-char SHA-256)
        validateFileHash(command.fileHash)

        // 3. Validate file size
        validateFileSize(command.fileSizeBytes)

        // 4. Validate declared media type
        validateDeclaredMediaType(command.declaredMediaType)

        // 5. Validate original filename
        validateOriginalFilename(command.declaredMediaType, command.originalFilename)

        // 6. Check if asset already exists — rate limit check moved after this
        //    so idempotent/202-polling retries do not burn creation quota
        val existingAsset = mediaAssetRepository.findByWorkspaceAndId(command.workspaceId, command.assetId)
        if (existingAsset != null) {
            return when {
                existingAsset.fileHash == command.fileHash -> {
                    // Idempotent: same assetId + same hash
                    PutAssetResult.AlreadyExists(
                        assetId = existingAsset.assetId,
                        workspaceId = existingAsset.workspaceId,
                        status = existingAsset.status.name,
                        mediaType = existingAsset.mediaType,
                        deduped = existingAsset.status == MediaAssetStatus.READY,
                        createdAt = ISO_FORMATTER.format(existingAsset.createdAt.atOffset(ZoneOffset.UTC)),
                    )
                }

                else -> {
                    // Hash mismatch
                    PutAssetResult.HashMismatch(
                        assetId = existingAsset.assetId,
                        existingFileHash = existingAsset.fileHash ?: "unknown",
                    )
                }
            }
        }

        // 8. Route based on whether blob already exists (read-only check, no insert).
        // Existed: handleExistedBlob manages its own transaction internally.
        // Created: upsertBlob + createPendingAsset in ONE atomic block so rollback is together.
        val existingBlob = workspaceFileBlobRepository.findByWorkspaceAndHash(command.workspaceId, command.fileHash)
        if (existingBlob != null) {
            return handleExistedBlob(command)
        }

        // Blob does not exist — create it and the pending asset atomically.
        // If createPendingAsset fails, the blob upsert is rolled back together.
        return transactionRunner.runAtomically {
            workspaceFileBlobRepository.upsertBlob(command.workspaceId, command.fileHash)
            createPendingAsset(command)
        }
    }

    private suspend fun handleExistedBlob(command: PutAssetCommand): PutAssetResult {
        // Fetch blob with lock inside transaction — authoritative read, not the pre-check snapshot
        val blob = transactionRunner.runAtomically {
            workspaceFileBlobRepository.findBlobForUpdate(command.workspaceId, command.fileHash)
                ?: throw BlobGoneException(command.fileHash)
        }
        return when (blob.status) {
            BlobStatus.READY -> {
                // Dedup hit — lock blob row and re-check READY status to prevent
                // a race where the blob transitioned to FAILED/READY_FOR_GC/GC
                // between upsertBlob's read and this asset creation.
                val now = Instant.now()
                transactionRunner.runAtomically {
                    val lockedBlob = workspaceFileBlobRepository
                        .findBlobForUpdate(command.workspaceId, command.fileHash)
                        ?: throw BlobGoneException(command.fileHash)

                    when (lockedBlob.status) {
                        BlobStatus.READY -> {
                            // Still READY — safe to dedup.
                            // First, check if another active asset already exists for this hash.
                            // If so, return THAT asset's id so the client collapses onto the
                            // canonical row instead of creating a duplicate asset row pointing
                            // at the same blob.
                            val existingAsset = mediaAssetRepository
                                .findActiveByWorkspaceAndHash(command.workspaceId, command.fileHash)
                            if (existingAsset != null && existingAsset.assetId != command.assetId) {
                                logger.info(
                                    "media.asset.put.dedup.existing assetId={} workspaceId={} fileHash={}",
                                    existingAsset.assetId,
                                    command.workspaceId,
                                    command.fileHash,
                                )
                                buildAlreadyExistsFromExisting(existingAsset)
                            } else {
                                val asset = MediaAsset(
                                    assetId = command.assetId,
                                    workspaceId = command.workspaceId,
                                    sourceType = MediaSourceType.UPLOADED,
                                    fileHash = command.fileHash,
                                    mediaType = command.declaredMediaType,
                                    storageKey = lockedBlob.storageKey!!,
                                    detectedMediaType = lockedBlob.detectedMediaType,
                                    originalFilename = command.originalFilename,
                                    fileSizeBytes = lockedBlob.fileSizeBytes,
                                    status = MediaAssetStatus.READY,
                                    createdAt = now,
                                )
                                mediaAssetRepository.create(asset)
                                logger.info(
                                    "media.asset.put.dedup assetId={} workspaceId={} fileHash={}",
                                    command.assetId,
                                    command.workspaceId,
                                    command.fileHash,
                                )
                                PutAssetResult.AlreadyExists(
                                    assetId = command.assetId,
                                    workspaceId = command.workspaceId,
                                    status = MediaAssetStatus.READY.name,
                                    mediaType = lockedBlob.detectedMediaType ?: command.declaredMediaType,
                                    deduped = true,
                                    createdAt = ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)),
                                )
                            }
                        }

                        BlobStatus.FAILED, BlobStatus.READY_FOR_GC, BlobStatus.GARBAGE_COLLECTED -> {
                            // Blob changed under our feet — reset and create pending
                            workspaceFileBlobRepository.resetBlobToUploading(
                                command.workspaceId,
                                command.fileHash,
                            )
                            createPendingAsset(command)
                        }

                        BlobStatus.UPLOADING -> {
                            // Another upload won the race
                            PutAssetResult.WaitingForBlob(
                                assetId = command.assetId,
                                retryAfterSeconds = 3,
                            )
                        }
                    }
                }
            }

            BlobStatus.UPLOADING -> {
                // Another upload in progress
                PutAssetResult.WaitingForBlob(
                    assetId = command.assetId,
                    retryAfterSeconds = 3,
                )
            }

            BlobStatus.FAILED, BlobStatus.READY_FOR_GC, BlobStatus.GARBAGE_COLLECTED -> {
                // Reset blob to UPLOADING and create asset as PENDING_UPLOAD
                workspaceFileBlobRepository.resetBlobToUploading(command.workspaceId, command.fileHash)
                createPendingAsset(command)
            }
        }
    }

    private suspend fun createPendingAsset(command: PutAssetCommand): PutAssetResult {
        // Rate limit check only when actually creating a new asset — not on 202 polling or idempotent retries
        enforceCreationRateLimit(command.workspaceId)

        val now = Instant.now()
        val asset = MediaAsset(
            assetId = command.assetId,
            workspaceId = command.workspaceId,
            sourceType = MediaSourceType.UPLOADED,
            fileHash = command.fileHash,
            mediaType = command.declaredMediaType,
            storageKey = null,
            originalFilename = command.originalFilename,
            fileSizeBytes = command.fileSizeBytes,
            status = MediaAssetStatus.PENDING_UPLOAD,
            createdAt = now,
        )
        mediaAssetRepository.create(asset)
        logger.info(
            "media.asset.put.created assetId={} workspaceId={} fileHash={} mediaType={}",
            command.assetId,
            command.workspaceId,
            command.fileHash,
            command.declaredMediaType,
        )
        return PutAssetResult.Created(
            assetId = command.assetId,
            workspaceId = command.workspaceId,
            status = MediaAssetStatus.PENDING_UPLOAD.name,
            mediaType = command.declaredMediaType,
            deduped = false,
            uploadUrl = "/api/media/assets/${command.assetId}/upload",
            createdAt = ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)),
        )
    }

    private fun buildAlreadyExistsFromExisting(existing: MediaAsset): PutAssetResult.AlreadyExists =
        PutAssetResult.AlreadyExists(
            assetId = existing.assetId,
            workspaceId = existing.workspaceId,
            status = existing.status.name,
            mediaType = existing.detectedMediaType ?: existing.mediaType,
            deduped = existing.status == MediaAssetStatus.READY,
            createdAt = ISO_FORMATTER.format(existing.createdAt.atOffset(ZoneOffset.UTC)),
        )

    private fun validateAssetId(assetId: String) {
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        if (!assetId.matches(uuidRegex)) {
            throw IllegalArgumentException("Invalid assetId format: must be a valid UUID v4")
        }
    }

    private fun validateFileHash(fileHash: String) {
        if (!MediaAsset.isValidHash(fileHash)) {
            throw IllegalArgumentException(
                "Invalid fileHash format: must be a lowercase 64-character SHA-256 hex string",
            )
        }
    }

    private fun validateFileSize(fileSizeBytes: Long) {
        if (fileSizeBytes < 1) {
            throw IllegalArgumentException("fileSizeBytes must be at least 1")
        }
        if (fileSizeBytes > MediaAsset.MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("fileSizeBytes exceeds maximum of ${MediaAsset.MAX_FILE_SIZE_BYTES}")
        }
    }

    private fun validateDeclaredMediaType(mediaType: String) {
        if (mediaType !in MediaAsset.SUPPORTED_MEDIA_TYPES) {
            val supportedTypes = MediaAsset.SUPPORTED_MEDIA_TYPES.joinToString()
            throw UnsupportedMediaTypeException(
                "Unsupported media type: $mediaType. Supported types: $supportedTypes",
                declaredType = mediaType,
            )
        }
    }

    private fun validateOriginalFilename(mediaType: String, originalFilename: String?) {
        if (mediaType !in MediaAsset.OFFICE_DOCUMENT_MEDIA_TYPES) return

        if (originalFilename.isNullOrBlank()) {
            throw UnsupportedMediaTypeException(
                "originalFilename is required for OOXML formats",
                declaredType = mediaType,
            )
        }

        val extension = originalFilename.substringAfterLast('.', "").lowercase()
        val validExtensions = when (mediaType) {
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            -> WORD_EXTENSIONS

            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            -> POWERPOINT_EXTENSIONS

            else -> emptySet()
        }

        if (extension !in validExtensions) {
            throw UnsupportedMediaTypeException(
                "Invalid file extension for OOXML format: $extension",
                declaredType = mediaType,
            )
        }
    }

    private suspend fun enforceCreationRateLimit(workspaceId: String) {
        val rateLimitOk = mediaRateLimitRepository.tryIncrementHourlyCreationCount(
            workspaceId,
            uploadSettings.maxCreationsPerHour,
        )
        if (!rateLimitOk) {
            throw RateLimitExceededException(
                workspaceId = workspaceId,
                limitType = "hourly_creations",
                currentValue = uploadSettings.maxCreationsPerHour,
                limitValue = uploadSettings.maxCreationsPerHour,
                retryAfterSeconds = HOURLY_CREATIONS_RETRY_AFTER_SECONDS,
            )
        }
    }
}

// ─── CAS Upload Asset Handler ────────────────────────────────────────────────

@Service
class CasUploadAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
    private val storageApplicationService: StorageApplicationService,
    private val uploadSettings: MediaUploadSettings,
    private val transactionRunner: AtomicTransactionRunner,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<CasUploadAssetCommand, CasUploadAssetResult> {

    private val logger = LoggerFactory.getLogger(CasUploadAssetHandler::class.java)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
        private const val JPEG_MAGIC_SIZE = 3
        private const val PNG_MAGIC_SIZE = 4
        private const val GIF_MAGIC_SIZE = 4
        private const val WEBP_MAGIC_SIZE = 12
        private const val MP4_MAGIC_SIZE = 8
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

    override suspend fun handle(command: CasUploadAssetCommand): CasUploadAssetResult {
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )
        val assetId = command.assetId
        val workspaceId = command.workspaceId

        // Step 1: claim upload slot
        val claimResult = claimUploadSlot(assetId, workspaceId)
        if (claimResult != null) return claimResult

        // Step 2: Stream bytes, compute hash, count bytes, detect type
        val tempKey = MediaStorageKeys.tempKey(workspaceId, assetId, command.declaredMediaType)

        val uploadOutcome = streamToTemp(command, tempKey)
        val actualBytes = uploadOutcome.actualBytes
        val detectedMediaType = uploadOutcome.detectedMediaType
        val computedHash = uploadOutcome.computedHash

        // Step 3: Verify byte count
        if (actualBytes != command.declaredFileSizeBytes) {
            cleanupTemp(tempKey)
            markBothFailed(assetId, workspaceId, "FILE_SIZE_MISMATCH")
            throw UploadFileSizeMismatchException(
                expected = command.declaredFileSizeBytes,
                actual = actualBytes,
            )
        }

        // Step 4: Verify hash
        if (computedHash != command.declaredFileHash) {
            cleanupTemp(tempKey)
            markBothFailed(assetId, workspaceId, "HASH_MISMATCH")
            throw UploadHashMismatchException(
                expected = command.declaredFileHash,
                actual = computedHash,
            )
        }

        // Step 5: Get asset to find fileHash
        val asset = mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
            ?: run {
                cleanupTemp(tempKey)
                markBothFailed(assetId, workspaceId, "ASSET_NOT_FOUND")
                return CasUploadAssetResult.NotFound(assetId)
            }
        val fileHash = asset.fileHash ?: run {
            cleanupTemp(tempKey)
            markBothFailed(assetId, workspaceId, "MISSING_FILE_HASH")
            return CasUploadAssetResult.NotFound(assetId)
        }

        // Step 6: Acquire lock on blob and finalize atomically.
        // The `FOR UPDATE` row lock must remain held until all subsequent DB writes commit,
        // otherwise concurrent requests can race between the SELECT and the UPDATEs and corrupt
        // the blob/asset state (data loss via premature READY_FOR_GC, or two requests both
        // claiming the canonical key). `transactionRunner.runAtomically` binds the lock to
        // the Reactor Context (Subscription), not the thread, which is the correct reactive
        // model.
        return try {
            transactionRunner.runAtomically {
                finalizeBlobWithinTransaction(
                    command = command,
                    assetId = assetId,
                    workspaceId = workspaceId,
                    fileHash = fileHash,
                    tempKey = tempKey,
                    detectedMediaType = detectedMediaType,
                    actualBytes = actualBytes,
                )
            } ?: throw BlobOrAssetMissingException(assetId)
        } catch (e: BlobOrAssetMissingException) {
            // The transactional block returned null because the blob was missing,
            // the asset was missing, or a required metadata field on the blob was null.
            // The transaction has already committed cleanly (no writes happened) so it is
            // safe to perform cleanup outside the transaction.
            cleanupTemp(tempKey)
            markBothFailed(assetId, workspaceId, "BLOB_OR_ASSET_MISSING")
            logger.warn(
                "media.asset.upload.transactionalEmpty assetId={} workspaceId={}",
                assetId,
                workspaceId,
            )
            CasUploadAssetResult.NotFound(assetId)
        } catch (e: StorageException) {
            // The transactional block terminated with an error (typically a `StorageException`
            // from `storageApplicationService.copyObject`). The transaction has already rolled
            // back via the transaction manager semantics. We now perform the storage cleanup
            // and mark the asset/blob as failed OUTSIDE the transaction.
            cleanupTemp(tempKey)
            markBothFailed(assetId, workspaceId, "COPY_FAILED")
            throw e
        }
    }

    /**
     * Internal sentinel exception used to signal "the inner finalize step observed a missing
     * blob/asset and wants the outer transaction to commit cleanly without writes". The outer
     * try/catch maps it back to a `NotFound` result.
     */
    private class BlobOrAssetMissingException(val assetId: String) :
        RuntimeException("Blob or asset missing for $assetId")

    /**
     * Inner finalize logic that runs inside the [transactionalOperator] subscription.
     *
     * Extracted so the entire `findBlobForUpdate` + status branching + DB mutation sequence
     * shares a single R2DBC transaction. Returns `null` for failure paths that must short-circuit
     * before mutating (e.g. blob missing, asset missing) so the caller can emit a `NotFound`
     * result without leaking intermediate state.
     */
    private suspend fun finalizeBlobWithinTransaction(
        command: CasUploadAssetCommand,
        assetId: String,
        workspaceId: String,
        fileHash: String,
        tempKey: String,
        detectedMediaType: String,
        actualBytes: Long,
    ): CasUploadAssetResult {
        // Step 6a: Acquire lock on blob
        val blob = workspaceFileBlobRepository.findBlobForUpdate(workspaceId, fileHash)
            ?: throw BlobOrAssetMissingException(assetId)

        return when (blob.status) {
            BlobStatus.READY -> {
                // Dedup hit: another upload completed first
                cleanupTemp(tempKey)
                val updatedAsset = mediaAssetRepository.markAsReadyFromDedup(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    storageKey = blob.storageKey ?: throw BlobOrAssetMissingException(assetId),
                    detectedMediaType = blob.detectedMediaType ?: throw BlobOrAssetMissingException(assetId),
                    fileSizeBytes = blob.fileSizeBytes,
                ) ?: throw BlobOrAssetMissingException(assetId)

                logger.info(
                    "media.asset.upload.dedupHit assetId={} workspaceId={} fileHash={}",
                    assetId,
                    workspaceId,
                    fileHash,
                )
                CasUploadAssetResult.Ready(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    status = MediaAssetStatus.READY.name,
                    mediaType = updatedAsset.detectedMediaType ?: command.declaredMediaType,
                    detectedMediaType = blob.detectedMediaType ?: command.declaredMediaType,
                    deduped = true,
                    fileSizeBytes = blob.fileSizeBytes ?: 0L,
                    createdAt = ISO_FORMATTER.format(updatedAsset.createdAt.atOffset(ZoneOffset.UTC)),
                )
            }

            BlobStatus.UPLOADING, BlobStatus.FAILED, BlobStatus.READY_FOR_GC, BlobStatus.GARBAGE_COLLECTED -> {
                // We won the race: finalize the blob
                val canonicalKey = MediaStorageKeys.canonicalKey(workspaceId, fileHash, detectedMediaType)

                // Copy temp → canonical. If this fails the transaction rolls back automatically
                // when the `transactional {}` Mono terminates with an error, so we do not need
                // an explicit rollback path here.
                try {
                    storageApplicationService.copyObject(
                        uploadSettings.storageBucket,
                        sourceKey = tempKey,
                        destKey = canonicalKey,
                    )
                } catch (e: StorageException) {
                    // Surface the exception to `transactional {}` so it rolls back the DB writes.
                    // Storage cleanup happens in the caller (outside the transaction) only after
                    // the transaction has settled to a known state.
                    throw e
                }

                // Delete temp
                cleanupTemp(tempKey)

                // Mark blob READY
                workspaceFileBlobRepository.markBlobReady(
                    workspaceId = workspaceId,
                    fileHash = fileHash,
                    storageKey = canonicalKey,
                    detectedMediaType = detectedMediaType,
                    fileSizeBytes = actualBytes,
                )

                // Mark asset READY
                val updatedAsset = mediaAssetRepository.markAsReadyFromDedup(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    storageKey = canonicalKey,
                    detectedMediaType = detectedMediaType,
                    fileSizeBytes = actualBytes,
                ) ?: throw BlobOrAssetMissingException(assetId)

                logger.info(
                    "media.asset.upload.completed assetId={} workspaceId={} fileHash={} " +
                        "canonicalKey={} detectedMediaType={} fileSizeBytes={}",
                    assetId,
                    workspaceId,
                    fileHash,
                    canonicalKey,
                    detectedMediaType,
                    actualBytes,
                )

                CasUploadAssetResult.Ready(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    status = MediaAssetStatus.READY.name,
                    mediaType = detectedMediaType,
                    detectedMediaType = detectedMediaType,
                    deduped = false,
                    fileSizeBytes = actualBytes,
                    createdAt = ISO_FORMATTER.format(updatedAsset.createdAt.atOffset(ZoneOffset.UTC)),
                )
            }
        }
    }

    private suspend fun claimUploadSlot(assetId: String, workspaceId: String): CasUploadAssetResult? {
        val asset = mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
            ?: return CasUploadAssetResult.NotFound(assetId)

        if (asset.status == MediaAssetStatus.DELETED) {
            return CasUploadAssetResult.NotFound(assetId)
        }

        if (asset.status == MediaAssetStatus.READY) {
            // Idempotent: already uploaded
            return CasUploadAssetResult.Ready(
                assetId = assetId,
                workspaceId = workspaceId,
                status = MediaAssetStatus.READY.name,
                mediaType = asset.detectedMediaType ?: asset.mediaType,
                detectedMediaType = asset.detectedMediaType ?: asset.mediaType,
                deduped = true,
                fileSizeBytes = asset.fileSizeBytes ?: 0L,
                createdAt = ISO_FORMATTER.format(asset.createdAt.atOffset(ZoneOffset.UTC)),
            )
        }

        val now = Instant.now()
        // Only PENDING_UPLOAD or FAILED can be claimed
        val claimable = asset.status == MediaAssetStatus.PENDING_UPLOAD || asset.status == MediaAssetStatus.FAILED

        if (!claimable) {
            if (asset.status == MediaAssetStatus.UPLOADING && asset.fileHash != null) {
                val blob = workspaceFileBlobRepository.findByWorkspaceAndHash(workspaceId, asset.fileHash)
                if (blob?.status == BlobStatus.READY) {
                    return null
                }
            }
            // Status is UPLOADING and the blob is not READY — another upload is still in progress
            return CasUploadAssetResult.UploadInProgress(assetId)
        }

        // Transition PENDING_UPLOAD/FAILED → UPLOADING
        val claimed = mediaAssetRepository.claimCasUploadSlot(assetId, workspaceId, now)
        if (!claimed) {
            // Race: another upload started — check current state
            val reloaded = mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
            if (reloaded?.status == MediaAssetStatus.READY) {
                return CasUploadAssetResult.Ready(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    status = MediaAssetStatus.READY.name,
                    mediaType = reloaded.detectedMediaType ?: reloaded.mediaType,
                    detectedMediaType = reloaded.detectedMediaType ?: reloaded.mediaType,
                    deduped = true,
                    fileSizeBytes = reloaded.fileSizeBytes ?: 0L,
                    createdAt = ISO_FORMATTER.format(reloaded.createdAt.atOffset(ZoneOffset.UTC)),
                )
            }
            return CasUploadAssetResult.UploadInProgress(assetId)
        }

        return null // Proceed with upload
    }

    private data class UploadOutcome(val actualBytes: Long, val detectedMediaType: String, val computedHash: String)

    private suspend fun streamToTemp(command: CasUploadAssetCommand, tempKey: String): UploadOutcome {
        val digest = MessageDigest.getInstance("SHA-256")
        var actualBytes = 0L
        var detectedMediaType: String? = null
        var validatedMagicBytes = false
        val pendingChunks = mutableListOf<ByteArray>()

        val maxBytes = command.declaredFileSizeBytes + 1 // reject anything over declared + 1
        val uploadFlow = flow {
            command.fileStream.collect { chunk ->
                actualBytes += chunk.size.toLong()

                // Early size guard: abort before digest.update to avoid wasting CPU on oversized uploads
                if (actualBytes > maxBytes) {
                    throw UploadFileSizeMismatchException(command.declaredFileSizeBytes, actualBytes)
                }

                // Update hash with every chunk
                digest.update(chunk)

                if (!validatedMagicBytes) {
                    pendingChunks.add(chunk)
                    val headerBytes = buildHeaderBytes(pendingChunks)
                    val detected = detectMediaType(headerBytes, command.declaredMediaType)
                    if (detected != null) {
                        detectedMediaType = detected
                        validatedMagicBytes = true
                        // Emit all pending
                        pendingChunks.forEach { emit(it) }
                        pendingChunks.clear()
                    } else if (headerBytes.size >= WEBP_MAGIC_SIZE) {
                        throw UnsupportedMediaTypeException(
                            "Magic-byte validation failed: detected unknown but declared ${command.declaredMediaType}",
                            declaredType = command.declaredMediaType,
                            detectedType = null,
                        )
                    }
                } else {
                    emit(chunk)
                }
            }

            // Handle case where magic bytes validated at end of stream
            if (!validatedMagicBytes) {
                val headerBytes = buildHeaderBytes(pendingChunks)
                val detected = detectMediaType(headerBytes, command.declaredMediaType)
                    ?: throw UnsupportedMediaTypeException(
                        "Magic-byte validation failed: detected unknown but declared ${command.declaredMediaType}",
                        declaredType = command.declaredMediaType,
                        detectedType = null,
                    )
                detectedMediaType = detected
                validatedMagicBytes = true
                pendingChunks.forEach { emit(it) }
                pendingChunks.clear()
            }
        }

        withTimeout(command.timeoutSeconds * MILLIS_PER_SECOND) {
            storageApplicationService.upload(
                bucket = uploadSettings.storageBucket,
                key = tempKey,
                content = uploadFlow,
                uploaderId = command.workspaceId,
                metadata = mapOf(
                    "assetId" to command.assetId,
                    "workspaceId" to command.workspaceId,
                    "declaredMediaType" to command.declaredMediaType,
                    "detectedMediaType" to (detectedMediaType ?: "pending"),
                ),
            )
        }

        val computedHash = digest.digest().toHexString()
        val effectiveMediaType = detectedMediaType ?: command.declaredMediaType

        return UploadOutcome(
            actualBytes = actualBytes,
            detectedMediaType = effectiveMediaType,
            computedHash = computedHash,
        )
    }

    private fun buildHeaderBytes(pendingChunks: List<ByteArray>): ByteArray = pendingChunks.asSequence()
        .flatMap { it.asSequence() }
        .take(WEBP_MAGIC_SIZE)
        .toList()
        .toByteArray()

    private fun detectMediaType(headerBytes: ByteArray, declaredType: String): String? {
        val sig = headerBytes.take(WEBP_MAGIC_SIZE).toByteArray()
        return when {
            hasPrefix(sig, JPEG_MAGIC, JPEG_MAGIC_SIZE) -> "image/jpeg"

            hasPrefix(sig, PNG_MAGIC, PNG_MAGIC_SIZE) -> "image/png"

            hasPrefix(sig, GIF_MAGIC, GIF_MAGIC_SIZE) -> "image/gif"

            hasOffsetPrefix(sig, RIFF_MAGIC, 0) && hasOffsetPrefix(sig, WEBP_MAGIC, RIFF_OFFSET) -> "image/webp"

            hasOffsetPrefix(sig, MP4_MAGIC, MP4_OFFSET) -> "video/mp4"

            hasPrefix(sig, OOXML_MAGIC, PNG_MAGIC_SIZE) -> declaredType

            // OOXML: trust declared type
            else -> null
        }
    }

    private fun hasPrefix(signature: ByteArray, magic: ByteArray, requiredSize: Int): Boolean =
        signature.size >= requiredSize &&
            magic.indices.all { signature[it] == magic[it] }

    private fun hasOffsetPrefix(signature: ByteArray, magic: ByteArray, offset: Int): Boolean {
        val requiredSize = offset + magic.size
        return signature.size >= requiredSize &&
            magic.indices.all { signature[offset + it] == magic[it] }
    }

    private suspend fun cleanupTemp(tempKey: String) {
        try {
            storageApplicationService.delete(
                bucket = uploadSettings.storageBucket,
                key = tempKey,
                deleterId = "upload-handler",
            )
        } catch (e: StorageException) {
            // Best-effort cleanup — log and move on
            logger.warn("Failed to cleanup temp key {}: {}", tempKey, e.message)
        }
    }

    private suspend fun markBothFailed(assetId: String, workspaceId: String, reason: String) {
        val asset = mediaAssetRepository.markAsFailed(assetId, workspaceId, reason)
        val fileHash = asset?.fileHash
        if (fileHash != null) {
            workspaceFileBlobRepository.markBlobFailed(workspaceId, fileHash, reason)
        }
        logger.info(
            "media.asset.upload.failed assetId={} workspaceId={} reason={}",
            assetId,
            workspaceId,
            reason,
        )
    }
}

// ─── Delete Asset Handler (CAS soft-delete) ────────────────────────────────

@Service
class DeleteAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
    private val transactionRunner: AtomicTransactionRunner,
) : CommandWithResultHandler<DeleteAssetCommand, DeleteAssetResult> {

    private val logger = LoggerFactory.getLogger(DeleteAssetHandler::class.java)

    override suspend fun handle(command: DeleteAssetCommand): DeleteAssetResult {
        val assetId = command.assetId
        val workspaceId = command.workspaceId

        // Step 1: Find asset
        val asset = mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)
            ?: throw AssetNotFoundException(assetId)

        // Step 2: Idempotent if already deleted
        if (asset.status == MediaAssetStatus.DELETED) {
            logger.info("media.asset.delete.idempotent assetId={} workspaceId={}", assetId, workspaceId)
            return DeleteAssetResult(deleted = true, blobScheduledForGC = false)
        }

        // Step 3: Soft-delete asset
        mediaAssetRepository.softDelete(assetId, workspaceId)
        logger.info("media.asset.delete.softDeleted assetId={} workspaceId={}", assetId, workspaceId)

        // Step 4: Get fileHash (nullable for pre-CAS assets)
        val fileHash = asset.fileHash ?: return DeleteAssetResult(deleted = true, blobScheduledForGC = false)

        // Step 5: Lock blob, count references, and (if zero) mark READY_FOR_GC — atomically.
        // Without this transaction, another request can create a new asset referencing the
        // same blob between the ref-count read and the `markReadyForGC` write, and we would
        // mark the blob for GC while it still has an active reference (data loss). The
        // `FOR UPDATE` row lock + atomic transaction scope guarantees that no other request
        // can observe an inconsistent intermediate state.
        return transactionRunner.runAtomically {
            countAndMaybeScheduleGc(workspaceId, fileHash)
        }
    }

    /**
     * Inner logic that runs inside the [transactionalOperator] subscription.
     *
     * Locks the blob row with `FOR UPDATE`, counts active references, and conditionally marks
     * the blob `READY_FOR_GC` if no active references remain. Runs in a single R2DBC transaction.
     */
    private suspend fun countAndMaybeScheduleGc(workspaceId: String, fileHash: String): DeleteAssetResult {
        val blob = workspaceFileBlobRepository.findBlobForUpdate(workspaceId, fileHash)
            ?: return DeleteAssetResult(deleted = true, blobScheduledForGC = false)

        val activeCount = mediaAssetRepository.countActiveReferences(workspaceId, fileHash)

        if (activeCount == 0) {
            val orphanedAt = Instant.now()
            workspaceFileBlobRepository.markReadyForGC(workspaceId, fileHash, orphanedAt)
            logger.info(
                "media.blob.markedReadyForGC workspaceId={} fileHash={}",
                workspaceId,
                fileHash,
            )
            return DeleteAssetResult(deleted = true, blobScheduledForGC = true)
        }

        return DeleteAssetResult(deleted = true, blobScheduledForGC = false)
    }
}

// ─── Exception helpers for upload ────────────────────────────────────────────

class UploadHashMismatchException(val expected: String, val actual: String) :
    RuntimeException("Hash mismatch: expected $expected, got $actual")

class UploadFileSizeMismatchException(val expected: Long, val actual: Long) :
    RuntimeException("File size mismatch: expected $expected bytes, got $actual bytes")

class BlobGoneException(val fileHash: String) :
    RuntimeException("Blob disappeared (likely GC'd) for fileHash=$fileHash")

// ─── Provider-sourced (EXTERNAL) imports ────────────────────────────────────

/**
 * Handler that imports a provider-sourced (currently Unsplash) asset through
 * the same CAS pipeline used by browser uploads.
 *
 * The handler is the single seam between the provider port and the media-library
 * `media_assets` table. It enforces three guards identical to the upload flow
 * (verified email, per-workspace rate limit, concurrent upload slot), computes
 * SHA-256 of the streamed bytes, looks up an existing READY blob for the same
 * `(workspaceId, fileHash)` pair (returning its canonical asset id with
 * `deduped: true`), and — on cache miss — copies the streamed payload into the
 * canonical CAS key and inserts both the blob row and an `EXTERNAL` asset row
 * with attribution in the same atomic transaction.
 *
 * Attribution:
 * - `sourceProvider`            ← `externalAsset.sourceProvider`
 * - `externalId`                ← `externalAsset.externalId.value`
 * - `sourceUrl`                 ← `externalAsset.sourceUrl`
 * - `authorName` / `authorUrl`  ← `externalAsset.authorName` / `authorUrl`
 * - `metadata`                  ← `externalAsset.metadata` + detected MIME / SHA-256
 */
@Service
class ImportExternalAssetHandler(
    private val mediaAssetRepository: MediaAssetRepository,
    private val workspaceFileBlobRepository: WorkspaceFileBlobRepository,
    private val storageApplicationService: StorageApplicationService,
    private val mediaProvider: com.profiletailors.smp.media.application.port.MediaProvider,
    private val uploadSettings: MediaUploadSettings,
    private val transactionRunner: AtomicTransactionRunner,
    private val mediaRateLimitRepository: MediaRateLimitRepository? = null,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<ImportExternalAssetCommand, ImportExternalAssetResult> {

    private val logger = LoggerFactory.getLogger(ImportExternalAssetHandler::class.java)

    override suspend fun handle(command: ImportExternalAssetCommand): ImportExternalAssetResult {
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )
        enforceConcurrentSlot(command.workspaceId)

        val external = command.externalAsset
        validateSupportedMediaType(external.mediaType)
        validateSize(external.contentLength)

        // Stream + hash
        val assetId = MediaAsset.generateAssetId()
        val tempKey = MediaStorageKeys.tempKey(command.workspaceId, assetId, external.mediaType)
        val outcome = streamToTempAndHash(external, tempKey)

        val fileHash = outcome.computedHash
        val actualBytes = outcome.actualBytes

        // Dedup check via the existing CAS blob repository
        return try {
            val result = transactionRunner.runAtomically {
                finalizeProviderBlob(
                    workspaceId = command.workspaceId,
                    assetId = assetId,
                    externalAsset = external,
                    fileHash = fileHash,
                    tempKey = tempKey,
                    detectedMediaType = outcome.detectedMediaType,
                    actualBytes = actualBytes,
                )
            } ?: throw BlobMissingException(fileHash)

            logger.info(
                "media.provider.import.completed assetId={} workspaceId={} externalId={} fileHash={} deduped={}",
                result.assetId,
                command.workspaceId,
                external.externalId.value,
                fileHash,
                result.deduped,
            )
            result
        } catch (@Suppress("SwallowedException") e: BlobMissingException) {
            // Either no active asset but no blob row, or the blob lifecycle is in an
            // unexpected state. Clean up storage and surface as a transient IMPORT_REJECTED.
            safeCleanupTemp(tempKey)
            throw UnsupportedMediaTypeException(
                "Provider import could not be finalized: missing blob for hash ${e.fileHash}",
                declaredType = external.mediaType,
                detectedType = outcome.detectedMediaType,
            )
        } catch (e: com.profiletailors.storage.domain.StorageException) {
            safeCleanupTemp(tempKey)
            throw e
        } finally {
            releaseConcurrentSlot(command.workspaceId)
        }
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    private suspend fun enforceConcurrentSlot(workspaceId: String) {
        if (mediaRateLimitRepository == null) return
        val claimed = mediaRateLimitRepository.tryClaimConcurrentUploadSlot(
            workspaceId,
            uploadSettings.maxConcurrentUploads,
        )
        if (!claimed) {
            throw RateLimitExceededException(
                workspaceId = workspaceId,
                limitType = "concurrent_uploads",
                currentValue = uploadSettings.maxConcurrentUploads,
                limitValue = uploadSettings.maxConcurrentUploads,
                retryAfterSeconds = 5,
            )
        }
    }

    private fun releaseConcurrentSlot(workspaceId: String) {
        // Best-effort: we use a separate coroutine-free path so a release failure
        // never breaks the import.
        try {
            kotlinx.coroutines.runBlocking {
                mediaRateLimitRepository?.releaseConcurrentUploadSlot(workspaceId)
            }
        } catch (_: Throwable) {
            // ignore — slot accounting is informational; the cleaner worker will eventually reconcile.
        }
    }

    // ── Streaming ────────────────────────────────────────────────────────────

    private data class StreamingOutcome(val actualBytes: Long, val detectedMediaType: String, val computedHash: String)

    private suspend fun streamToTempAndHash(
        external: com.profiletailors.smp.media.application.port.ProviderExternalAsset,
        tempKey: String,
    ): StreamingOutcome {
        val digest = MessageDigest.getInstance("SHA-256")
        var actualBytes = 0L
        val pending = mutableListOf<ByteArray>()
        var validatedMagic = false
        var detectedMediaType: String? = null

        external.bytes.collect { chunk ->
            actualBytes += chunk.size.toLong()
            if (actualBytes > MediaAsset.MAX_FILE_SIZE_BYTES) {
                throw FileTooLargeException(actualBytes, MediaAsset.MAX_FILE_SIZE_BYTES)
            }
            digest.update(chunk)
            if (!validatedMagic) {
                pending += chunk
                val header = pending.fold(ByteArray(0)) { acc, b -> acc + b }
                val detected = detectImageMediaType(header)
                if (detected != null) {
                    detectedMediaType = detected
                    validatedMagic = true
                } else if (header.size >= MAX_PROBE_BYTES) {
                    throw UnsupportedMediaTypeException(
                        "Provider stream is not a supported image MIME",
                        declaredType = external.mediaType,
                        detectedType = null,
                    )
                }
            }
        }

        if (!validatedMagic) {
            val header = pending.fold(ByteArray(0)) { acc, b -> acc + b }
            detectedMediaType = detectImageMediaType(header)
                ?: throw UnsupportedMediaTypeException(
                    "Provider stream is not a supported image MIME",
                    declaredType = external.mediaType,
                    detectedType = null,
                )
        }

        // Replay into storage now that we know it's a valid image.
        val replayBytes = mutableListOf<ByteArray>()
        external.bytes.collect { replayBytes += it }
        storageApplicationService.upload(
            bucket = uploadSettings.storageBucket,
            key = tempKey,
            content = kotlinx.coroutines.flow.flow {
                replayBytes.forEach { emit(it) }
            },
            uploaderId = "provider:${external.sourceProvider}",
        )

        return StreamingOutcome(
            actualBytes = actualBytes,
            detectedMediaType = detectedMediaType!!,
            computedHash = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    @Suppress("ReturnCount") // Early returns keep each independent file signature readable.
    private fun detectImageMediaType(header: ByteArray): String? {
        if (matchesJpegMagic(header)) return JPEG_MEDIA_TYPE
        if (matchesPngMagic(header)) return PNG_MEDIA_TYPE
        if (matchesGifMagic(header)) return GIF_MEDIA_TYPE
        if (matchesWebpMagic(header)) return WEBP_MEDIA_TYPE
        return null
    }

    private fun matchesJpegMagic(header: ByteArray): Boolean = header.size >= JPEG_MAGIC.size &&
        header[JPEG_SOI_0] == JPEG_MAGIC[JPEG_SOI_0] &&
        header[JPEG_SOI_1] == JPEG_MAGIC[JPEG_SOI_1] &&
        header[JPEG_SOI_2] == JPEG_MAGIC[JPEG_SOI_2]

    private fun matchesPngMagic(header: ByteArray): Boolean = header.size >= PNG_MAGIC.size &&
        header[PNG_OFFSET_0] == PNG_MAGIC[PNG_OFFSET_0] &&
        header[PNG_OFFSET_1] == PNG_MAGIC[PNG_OFFSET_1] &&
        header[PNG_OFFSET_2] == PNG_MAGIC[PNG_OFFSET_2] &&
        header[PNG_OFFSET_3] == PNG_MAGIC[PNG_OFFSET_3]

    private fun matchesGifMagic(header: ByteArray): Boolean = header.size >= GIF_MAGIC.size &&
        header[GIF_OFFSET_0] == GIF_MAGIC[GIF_OFFSET_0] &&
        header[GIF_OFFSET_1] == GIF_MAGIC[GIF_OFFSET_1] &&
        header[GIF_OFFSET_2] == GIF_MAGIC[GIF_OFFSET_2]

    private fun matchesWebpMagic(header: ByteArray): Boolean = header.size >= WEBP_MAGIC.size &&
        header[WEBP_RIFF_0] == WEBP_MAGIC[WEBP_RIFF_0] &&
        header[WEBP_RIFF_1] == WEBP_MAGIC[WEBP_RIFF_1] &&
        header[WEBP_RIFF_2] == WEBP_MAGIC[WEBP_RIFF_2] &&
        header[WEBP_RIFF_3] == WEBP_MAGIC[WEBP_RIFF_3] &&
        header[WEBP_WEBP_0] == WEBP_MAGIC[WEBP_WEBP_0] &&
        header[WEBP_WEBP_1] == WEBP_MAGIC[WEBP_WEBP_1] &&
        header[WEBP_WEBP_2] == WEBP_MAGIC[WEBP_WEBP_2] &&
        header[WEBP_WEBP_3] == WEBP_MAGIC[WEBP_WEBP_3]

    // ── Finalization (transactional) ──────────────────────────────────────────

    @Suppress("LongMethod") // Keeps the CAS state transition and asset creation in one auditable transaction.
    private suspend fun finalizeProviderBlob(
        workspaceId: String,
        assetId: String,
        externalAsset: com.profiletailors.smp.media.application.port.ProviderExternalAsset,
        fileHash: String,
        tempKey: String,
        detectedMediaType: String,
        actualBytes: Long,
    ): ImportExternalAssetResult {
        // If an active asset already exists for this (workspace, fileHash), reuse it — dedup hit.
        val existing = mediaAssetRepository.findActiveByWorkspaceAndHash(workspaceId, fileHash)
        if (existing != null) {
            safeCleanupTemp(tempKey)
            return ImportExternalAssetResult(
                assetId = existing.assetId,
                workspaceId = workspaceId,
                deduped = true,
                mediaType = existing.detectedMediaType ?: detectedMediaType,
                fileSizeBytes = existing.fileSizeBytes ?: actualBytes,
            )
        }

        val blob = workspaceFileBlobRepository.findBlobForUpdate(workspaceId, fileHash)
            ?: run {
                workspaceFileBlobRepository.upsertBlob(workspaceId, fileHash)
                workspaceFileBlobRepository.findBlobForUpdate(workspaceId, fileHash)
                    ?: throw BlobMissingException(fileHash)
            }

        return when (blob.status) {
            BlobStatus.READY -> {
                // Existing READY blob — dedup hit, find the canonical asset for it
                safeCleanupTemp(tempKey)
                val canonical = mediaAssetRepository.findActiveByWorkspaceAndHash(workspaceId, fileHash)
                if (canonical != null) {
                    ImportExternalAssetResult(
                        assetId = canonical.assetId,
                        workspaceId = workspaceId,
                        deduped = true,
                        mediaType = canonical.detectedMediaType ?: detectedMediaType,
                        fileSizeBytes = canonical.fileSizeBytes ?: actualBytes,
                    )
                } else {
                    // Orphan READY blob — still treat as dedup hit using the new assetId
                    val canonicalKey = MediaStorageKeys.canonicalKey(workspaceId, fileHash, detectedMediaType)
                    val newAsset = MediaAsset(
                        assetId = assetId,
                        workspaceId = workspaceId,
                        sourceType = MediaSourceType.EXTERNAL,
                        fileHash = fileHash,
                        mediaType = detectedMediaType,
                        storageKey = canonicalKey,
                        detectedMediaType = detectedMediaType,
                        fileSizeBytes = actualBytes,
                        status = MediaAssetStatus.READY,
                        sourceProvider = externalAsset.sourceProvider,
                        externalId = externalAsset.externalId.value,
                        sourceUrl = externalAsset.sourceUrl,
                        authorName = externalAsset.authorName,
                        authorUrl = externalAsset.authorUrl,
                        metadata = buildMetadata(externalAsset, detectedMediaType),
                        createdAt = Instant.now(),
                    )
                    mediaAssetRepository.create(newAsset)
                    ImportExternalAssetResult(
                        assetId = assetId,
                        workspaceId = workspaceId,
                        deduped = true,
                        mediaType = detectedMediaType,
                        fileSizeBytes = actualBytes,
                    )
                }
            }

            BlobStatus.UPLOADING, BlobStatus.FAILED, BlobStatus.READY_FOR_GC, BlobStatus.GARBAGE_COLLECTED -> {
                // We won the race — finalize the blob
                val canonicalKey = MediaStorageKeys.canonicalKey(workspaceId, fileHash, detectedMediaType)
                storageApplicationService.copyObject(
                    uploadSettings.storageBucket,
                    sourceKey = tempKey,
                    destKey = canonicalKey,
                )
                safeCleanupTemp(tempKey)
                workspaceFileBlobRepository.markBlobReady(
                    workspaceId = workspaceId,
                    fileHash = fileHash,
                    storageKey = canonicalKey,
                    detectedMediaType = detectedMediaType,
                    fileSizeBytes = actualBytes,
                )
                val newAsset = MediaAsset(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    sourceType = MediaSourceType.EXTERNAL,
                    fileHash = fileHash,
                    mediaType = detectedMediaType,
                    storageKey = canonicalKey,
                    detectedMediaType = detectedMediaType,
                    fileSizeBytes = actualBytes,
                    status = MediaAssetStatus.READY,
                    sourceProvider = externalAsset.sourceProvider,
                    externalId = externalAsset.externalId.value,
                    sourceUrl = externalAsset.sourceUrl,
                    authorName = externalAsset.authorName,
                    authorUrl = externalAsset.authorUrl,
                    metadata = buildMetadata(externalAsset, detectedMediaType),
                    createdAt = Instant.now(),
                )
                mediaAssetRepository.create(newAsset)
                ImportExternalAssetResult(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    deduped = false,
                    mediaType = detectedMediaType,
                    fileSizeBytes = actualBytes,
                )
            }
        }
    }

    // ── Validation helpers ──────────────────────────────────────────────────

    private fun validateSupportedMediaType(mediaType: String) {
        if (mediaType !in MediaAsset.SUPPORTED_MEDIA_TYPES) {
            val supported = MediaAsset.SUPPORTED_MEDIA_TYPES.joinToString()
            throw UnsupportedMediaTypeException(
                "Unsupported provider MIME type: $mediaType. Supported: $supported",
                declaredType = mediaType,
            )
        }
    }

    private fun validateSize(contentLength: Long) {
        if (contentLength < 0) {
            throw IllegalArgumentException("contentLength must be non-negative")
        }
        if (contentLength > MediaAsset.MAX_FILE_SIZE_BYTES) {
            throw FileTooLargeException(contentLength, MediaAsset.MAX_FILE_SIZE_BYTES)
        }
    }

    private suspend fun safeCleanupTemp(tempKey: String) {
        try {
            storageApplicationService.delete(
                bucket = uploadSettings.storageBucket,
                key = tempKey,
                deleterId = "provider-cleanup",
            )
        } catch (_: Throwable) {
            // ignore
        }
    }

    private class BlobMissingException(val fileHash: String) :
        RuntimeException("Provider import could not finalize — blob missing for $fileHash")

    companion object {
        private const val MAX_PROBE_BYTES = 16

        // Magic-byte signatures for image formats detected by probe bytes.
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        private val GIF_MAGIC = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte())
        private val WEBP_MAGIC = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(),
        )

        private const val JPEG_SOI_0 = 0
        private const val JPEG_SOI_1 = 1
        private const val JPEG_SOI_2 = 2
        private const val PNG_OFFSET_0 = 0
        private const val PNG_OFFSET_1 = 1
        private const val PNG_OFFSET_2 = 2
        private const val PNG_OFFSET_3 = 3
        private const val GIF_OFFSET_0 = 0
        private const val GIF_OFFSET_1 = 1
        private const val GIF_OFFSET_2 = 2
        private const val WEBP_RIFF_0 = 0
        private const val WEBP_RIFF_1 = 1
        private const val WEBP_RIFF_2 = 2
        private const val WEBP_RIFF_3 = 3
        private const val WEBP_WEBP_0 = 8
        private const val WEBP_WEBP_1 = 9
        private const val WEBP_WEBP_2 = 10
        private const val WEBP_WEBP_3 = 11

        private const val JPEG_MEDIA_TYPE = "image/jpeg"
        private const val PNG_MEDIA_TYPE = "image/png"
        private const val GIF_MEDIA_TYPE = "image/gif"
        private const val WEBP_MEDIA_TYPE = "image/webp"

        private fun buildMetadata(
            external: com.profiletailors.smp.media.application.port.ProviderExternalAsset,
            detectedMediaType: String,
        ): Map<String, Any> {
            val base: MutableMap<String, Any> = LinkedHashMap()
            external.metadata?.forEach { (k, v) -> if (v != null) base[k] = v }
            base["provider"] = external.sourceProvider
            base["detectedMediaType"] = detectedMediaType
            base["importedAt"] = Instant.now().toString()
            return base
        }
    }
}

// ─── Provider search query ───────────────────────────────────────────────────

/**
 * Locates the [MediaProvider] for the requested provider id and delegates the
 * search. Throws [UnsupportedProviderException] when no adapter is registered
 * for the id, so the controller layer can map it to 404.
 *
 * Verified-email guard is enforced at the controller boundary (per design: the
 * `SearchProviderPhotosQuery` is for the read-path and does not need the same
 * strict rate-limit / concurrent-slot guards as the write-path import). When
 * the email-verification requirement changes this becomes a non-issue.
 */
@Service
class SearchProviderPhotosHandler(private val providers: List<MediaProvider>) :
    QueryHandler<SearchProviderPhotosQuery, ProviderSearchPage> {

    private val logger = LoggerFactory.getLogger(SearchProviderPhotosHandler::class.java)

    override suspend fun handle(query: SearchProviderPhotosQuery): ProviderSearchPage {
        val provider = providers.firstOrNull { it.providerId == query.providerId }
            ?: throw UnsupportedProviderException(query.providerId)

        logger.info(
            "media.provider.search providerId={} workspaceId={} query={} page={}",
            query.providerId,
            query.workspaceId,
            query.query,
            query.page,
        )
        return provider.search(query = query.query, page = query.page)
    }
}

/**
 * Resolves a fully-qualified external id (e.g. `unsplash:abc123`) into a
 * [ProviderExternalAsset] by delegating to the registered `MediaProvider`
 * adapter. Used by the controller to fetch provider bytes before dispatching
 * [ImportExternalAssetCommand].
 *
 * Throws [UnsupportedProviderException] when the requested provider has no
 * registered adapter — the controller maps this to HTTP 404.
 */
@Service
class ImportProviderAssetHandler(private val providers: List<MediaProvider>) :
    QueryHandler<ImportProviderAssetQuery, com.profiletailors.smp.media.application.port.ProviderExternalAsset> {

    private val logger = LoggerFactory.getLogger(ImportProviderAssetHandler::class.java)

    override suspend fun handle(
        query: ImportProviderAssetQuery,
    ): com.profiletailors.smp.media.application.port.ProviderExternalAsset {
        val provider = providers.firstOrNull { it.providerId == query.providerId }
            ?: throw UnsupportedProviderException(query.providerId)

        val externalId = ProviderExternalId(query.externalId)

        logger.info(
            "media.provider.fetch providerId={} workspaceId={} externalId={}",
            query.providerId,
            query.workspaceId,
            query.externalId,
        )
        return provider.import(workspaceId = query.workspaceId, externalId = externalId)
    }
}
