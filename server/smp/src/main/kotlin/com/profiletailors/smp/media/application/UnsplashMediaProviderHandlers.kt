package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.permissivePrincipalContextProvider
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import com.profiletailors.storage.application.StorageApplicationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class SearchUnsplashPhotosHandler(private val provider: UnsplashPhotoProvider) :
    QueryHandler<SearchUnsplashPhotosQuery, List<UnsplashPhoto>> {
    override suspend fun handle(query: SearchUnsplashPhotosQuery): List<UnsplashPhoto> =
        provider.search(query.query?.trim()?.takeIf(String::isNotEmpty))
}

@Service
class ImportUnsplashPhotoHandler(
    private val provider: UnsplashPhotoProvider,
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val storageApplicationService: StorageApplicationService,
    private val settings: UnsplashImportSettings,
    private val assetPreviewUrlResolver: AssetPreviewUrlResolver,
    private val mediaPreviewTokenService: MediaPreviewTokenService,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<ImportUnsplashPhotoCommand, MediaAssetSummary> {
    override suspend fun handle(command: ImportUnsplashPhotoCommand): MediaAssetSummary {
        requireEmailVerification(
            principalContextProvider.require(),
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )
        enforceCreationRateLimit(command.workspaceId)

        val photo = provider.get(command.externalId)
        val assetId = MediaAsset.generateAssetId()
        val storageKey = MediaAsset.generateStorageKey(command.workspaceId, assetId)
        var fileSizeBytes = 0L

        val guardedContent = flow {
            provider.download(photo).collect { chunk ->
                fileSizeBytes += chunk.size
                if (fileSizeBytes > settings.maxFileSizeBytes) {
                    throw UnsplashPhotoTooLargeException(fileSizeBytes, settings.maxFileSizeBytes)
                }
                emit(chunk)
            }
        }

        try {
            storageApplicationService.upload(
                bucket = settings.storageBucket,
                key = storageKey,
                content = guardedContent,
                uploaderId = command.workspaceId,
                metadata = mapOf(
                    "assetId" to assetId,
                    "workspaceId" to command.workspaceId,
                    "sourceProvider" to UNSPLASH_PROVIDER,
                    "externalId" to photo.externalId,
                ),
            )
            provider.trackDownload(photo)

            val asset = persistPhoto(command, photo, assetId, storageKey, fileSizeBytes)
            return asset.toUnsplashSummary(assetPreviewUrlResolver, mediaPreviewTokenService)
        } catch (exception: CancellationException) {
            cleanupStorage(storageKey)
            throw exception
        } catch (exception: Exception) {
            cleanupStorage(storageKey)
            throw exception
        }
    }

    private suspend fun persistPhoto(
        command: ImportUnsplashPhotoCommand,
        photo: UnsplashPhoto,
        assetId: String,
        storageKey: String,
        fileSizeBytes: Long,
    ): MediaAsset = mediaAssetRepository.create(
        MediaAsset(
            assetId = assetId,
            workspaceId = command.workspaceId,
            sourceType = MediaSourceType.EXTERNAL,
            fileHash = null,
            mediaType = JPEG_MEDIA_TYPE,
            detectedMediaType = JPEG_MEDIA_TYPE,
            storageKey = storageKey,
            originalFilename = "unsplash-${photo.externalId}.jpg",
            fileSizeBytes = fileSizeBytes,
            status = MediaAssetStatus.READY,
            createdAt = Instant.now(),
            sourceProvider = UNSPLASH_PROVIDER,
            externalId = photo.externalId,
            sourceUrl = photo.sourceUrl,
            authorName = photo.authorName,
            authorUrl = photo.authorUrl,
            metadata = mapOf("downloadLocation" to photo.downloadLocation),
        ),
    )

    private suspend fun cleanupStorage(storageKey: String) {
        runCatching {
            storageApplicationService.delete(
                bucket = settings.storageBucket,
                key = storageKey,
                deleterId = "unsplash-import",
            )
        }
    }

    private suspend fun enforceCreationRateLimit(workspaceId: String) {
        val rateLimitOk = mediaRateLimitRepository.tryIncrementHourlyCreationCount(
            workspaceId,
            settings.maxCreationsPerHour,
        )
        if (!rateLimitOk) {
            throw RateLimitExceededException(
                workspaceId = workspaceId,
                limitType = "hourly_creations",
                currentValue = settings.maxCreationsPerHour,
                limitValue = settings.maxCreationsPerHour,
                retryAfterSeconds = HOURLY_CREATIONS_RETRY_AFTER_SECONDS,
            )
        }
    }

    private suspend fun MediaAsset.toUnsplashSummary(
        previewUrlResolver: AssetPreviewUrlResolver,
        previewTokenService: MediaPreviewTokenService,
    ): MediaAssetSummary = MediaAssetSummary(
        assetId = assetId,
        workspaceId = workspaceId,
        mediaType = mediaType,
        sourceType = sourceType.name,
        status = status.name,
        originalFilename = originalFilename,
        fileSizeBytes = fileSizeBytes,
        fileHash = fileHash,
        createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt.atOffset(ZoneOffset.UTC)),
        previewUrl = previewUrlResolver.resolvePreviewUrl(
            assetId = assetId,
            workspaceId = workspaceId,
            mediaType = mediaType,
            storageKey = storageKey,
            externalUrl = null,
        ),
        downloadUrl = previewTokenService.buildSignedContentPath(assetId, workspaceId),
        sourceProvider = sourceProvider,
        externalId = externalId,
        sourceUrl = sourceUrl,
        authorName = authorName,
        authorUrl = authorUrl,
        metadata = metadata,
    )

    private companion object {
        const val UNSPLASH_PROVIDER = "unsplash"
        const val JPEG_MEDIA_TYPE = "image/jpeg"
        const val HOURLY_CREATIONS_RETRY_AFTER_SECONDS = 3_600
    }
}
