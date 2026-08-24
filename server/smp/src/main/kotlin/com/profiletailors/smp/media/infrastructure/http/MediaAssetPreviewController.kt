package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.smp.media.application.AssetNotFoundException
import com.profiletailors.smp.media.application.MediaAssetPreviewPort
import com.profiletailors.smp.media.application.MediaPreviewTokenService
import com.profiletailors.smp.media.application.MediaUploadSettings
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.storage.infrastructure.asFlux
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@RequestMapping(value = ["/api/media/assets"])
@Tag(name = "Media Preview", description = "Signed public preview URLs for media assets")
class MediaAssetPreviewController(
    private val mediaAssetPreviewPort: MediaAssetPreviewPort,
    private val mediaPreviewTokenService: MediaPreviewTokenService,
    private val mediaUploadSettings: MediaUploadSettings,
) {
    companion object {
        private const val HTTP_FORBIDDEN = 403
        private const val PREVIEW_CACHE_MINUTES = 15L
    }

    @Operation(summary = "Stream a signed public preview for an image asset")
    @GetMapping("/{assetId}/preview")
    suspend fun previewAsset(
        @PathVariable assetId: String,
        @RequestParam workspaceId: String,
        @RequestParam expiresAt: Long,
        @RequestParam signature: String,
    ): ResponseEntity<Flux<DataBuffer>> {
        val asset = validateAndLoadAsset(assetId, workspaceId, expiresAt, signature)

        val storageKey = asset?.storageKey
        return when {
            asset == null -> forbiddenResponse()

            storageKey == null -> ResponseEntity.notFound().build()

            !isReadyImage(asset.mediaType, asset.status) -> ResponseEntity.notFound().build()

            else -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.mediaType))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(PREVIEW_CACHE_MINUTES)))
                .body(downloadBody(assetId, storageKey, "media-preview"))
        }
    }

    @Operation(summary = "Stream signed asset content for image/video/pdf preview or download")
    @GetMapping("/{assetId}/content")
    suspend fun contentAsset(
        @PathVariable assetId: String,
        @RequestParam workspaceId: String,
        @RequestParam expiresAt: Long,
        @RequestParam signature: String,
    ): ResponseEntity<Flux<DataBuffer>> {
        val asset = validateAndLoadAsset(assetId, workspaceId, expiresAt, signature)

        return when {
            asset == null -> forbiddenResponse()

            asset.status != MediaAssetStatus.READY -> ResponseEntity.notFound().build()

            else -> {
                val headers = HttpHeaders().apply {
                    contentType = MediaType.parseMediaType(asset.mediaType)
                    cacheControl = CacheControl.maxAge(Duration.ofMinutes(PREVIEW_CACHE_MINUTES)).headerValue
                    contentDisposition = when {
                        asset.mediaType.equals("application/pdf", ignoreCase = true) ->
                            ContentDisposition.inline().filename(asset.originalFilename ?: "$assetId.pdf").build()

                        asset.mediaType.startsWith("video/", ignoreCase = true) ->
                            ContentDisposition.inline().filename(asset.originalFilename ?: "$assetId.mp4").build()

                        else ->
                            ContentDisposition.attachment().filename(asset.originalFilename ?: assetId).build()
                    }
                }

                val storageKey = asset.storageKey
                    ?: return ResponseEntity.notFound().build()
                ResponseEntity.ok()
                    .headers(headers)
                    .body(downloadBody(assetId, storageKey, "media-content"))
            }
        }
    }

    private suspend fun validateAndLoadAsset(
        assetId: String,
        workspaceId: String,
        expiresAt: Long,
        signature: String,
    ) = if (mediaPreviewTokenService.isValid(assetId, workspaceId, expiresAt, signature)) {
        mediaAssetPreviewPort.findAsset(workspaceId, assetId)
            ?: throw AssetNotFoundException(assetId)
    } else {
        null
    }

    private fun forbiddenResponse(): ResponseEntity<Flux<DataBuffer>> = ResponseEntity.status(HTTP_FORBIDDEN).build()

    private fun isReadyImage(mediaType: String, status: MediaAssetStatus): Boolean =
        status == MediaAssetStatus.READY && mediaType.startsWith("image/", ignoreCase = true)

    private suspend fun downloadBody(assetId: String, storageKey: String, purpose: String): Flux<DataBuffer> =
        mediaAssetPreviewPort
            .download(
                bucket = mediaUploadSettings.storageBucket,
                key = storageKey,
                downloaderId = "$purpose:$assetId",
            )
            .asFlux()
}
