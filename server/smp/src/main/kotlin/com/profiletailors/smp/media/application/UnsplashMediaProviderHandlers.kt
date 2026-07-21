package com.profiletailors.smp.media.application

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SearchUnsplashPhotosHandler(private val provider: UnsplashPhotoProvider) :
    QueryHandler<SearchUnsplashPhotosQuery, List<UnsplashPhoto>> {
    /**
     * Searches Unsplash photos using the normalized query text.
     *
     * @param query The search query whose text is trimmed before searching.
     * @return The matching Unsplash photos.
     */
    override suspend fun handle(query: SearchUnsplashPhotosQuery): List<UnsplashPhoto> =
        provider.search(query.query?.trim()?.takeIf(String::isNotEmpty))
}

class MediaImportService(
    private val provider: UnsplashPhotoProvider,
    private val mediaAssetRepository: MediaAssetRepository,
    private val storagePort: MediaStoragePort,
    private val settings: UnsplashImportSettings,
    private val assetPreviewUrlResolver: AssetPreviewUrlResolver,
    private val mediaPreviewTokenService: MediaPreviewTokenService,
) {
    /**
     * Imports an Unsplash photo as a media asset for the specified workspace.
     *
     * @param command The import command containing the workspace and Unsplash photo identifier.
     * @return A summary of the imported media asset.
     * @throws UnsplashPhotoTooLargeException If the downloaded photo exceeds the configured maximum file size.
     */
    suspend fun importUnsplashPhoto(command: ImportUnsplashPhotoCommand): MediaAssetSummary {
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

        val asset = try {
            storagePort.upload(
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
            persistPhoto(command, photo, assetId, storageKey, fileSizeBytes)
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { cleanupStorage(storageKey) }
            throw exception
        } catch (exception: Exception) {
            withContext(NonCancellable) { cleanupStorage(storageKey) }
            throw exception
        }

        return asset.toUnsplashSummary(assetPreviewUrlResolver, mediaPreviewTokenService)
    }

    /**
     * Persists an imported Unsplash photo as a ready media asset.
     *
     * @param command The import command containing the target workspace.
     * @param photo The Unsplash photo to persist.
     * @param assetId The generated media asset identifier.
     * @param storageKey The storage location of the imported photo.
     * @param fileSizeBytes The size of the stored photo in bytes.
     * @return The persisted media asset.
     */
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
            licence = UNSPLASH_PROVIDER,
        ),
    )

    /**
     * Attempts to remove an imported object from storage.
     *
     * @param storageKey The key of the object to remove.
     */
    private suspend fun cleanupStorage(storageKey: String) {
        runCatching {
            storagePort.delete(
                bucket = settings.storageBucket,
                key = storageKey,
                deleterId = "unsplash-import",
            )
        }
    }

    /**
     * Converts this media asset into an Unsplash media asset summary.
     *
     * @param previewUrlResolver Resolves the asset preview URL.
     * @param previewTokenService Builds the signed download path.
     * @return A summary containing the asset details and resolved access URLs.
     */
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
        licence = licence,
    )

    private companion object {
        const val UNSPLASH_PROVIDER = "unsplash"
        const val JPEG_MEDIA_TYPE = "image/jpeg"
    }
}

class ImportUnsplashPhotoHandler(
    private val mediaRateLimitRepository: MediaRateLimitRepository,
    private val mediaImportService: MediaImportService,
    private val settings: UnsplashImportSettings,
    private val principalContextProvider: PrincipalContextProvider = permissivePrincipalContextProvider(),
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<ImportUnsplashPhotoCommand, MediaAssetSummary> {
    /**
     * Authorizes and rate-limits Unsplash import requests before delegating the media import.
     *
     * @param command The command containing the workspace and Unsplash photo identifier.
     * @return A summary of the imported media asset.
     */
    override suspend fun handle(command: ImportUnsplashPhotoCommand): MediaAssetSummary {
        val principalContext = principalContextProvider.require()
        requireEmailVerification(
            principalContext,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.UPLOAD_MEDIA,
        )
        enforceCreationRateLimit(command.workspaceId)
        return mediaImportService.importUnsplashPhoto(command)
    }

    /**
     * Enforces the hourly media creation limit for a workspace.
     *
     * @param workspaceId The workspace whose creation limit is checked.
     * @throws RateLimitExceededException If the workspace has reached the hourly creation limit.
     * @return The current creation count after the increment.
     */
    private suspend fun enforceCreationRateLimit(workspaceId: String): Int = enforceHourlyCreationRateLimit(
        workspaceId = workspaceId,
        mediaRateLimitRepository = mediaRateLimitRepository,
        maxCreationsPerHour = settings.maxCreationsPerHour,
    )
}
