package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.media.application.AssetNotFoundException
import com.profiletailors.smp.media.application.MediaAssetStreamResponse
import com.profiletailors.smp.media.application.MediaUploadSettings
import com.profiletailors.smp.media.application.StreamMediaAssetQuery
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.storage.application.StorageApplicationService
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
    private val mediator: Mediator,
    private val storageApplicationService: StorageApplicationService,
    private val mediaUploadSettings: MediaUploadSettings,
) {
    companion object {
        private const val HTTP_FORBIDDEN = 403
        private const val PREVIEW_CACHE_MINUTES = 15L
    }

    @Operation(summary = "Stream a signed public preview for an image asset")
    @GetMapping("/{assetId}/preview")
    @Suppress("SwallowedException")
    suspend fun previewAsset(
        @PathVariable assetId: String,
        @RequestParam workspaceId: String,
        @RequestParam expiresAt: Long,
        @RequestParam signature: String,
    ): ResponseEntity<Flux<DataBuffer>> {
        val response = try {
            mediator.send(
                StreamMediaAssetQuery(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    expiresAt = expiresAt,
                    signature = signature,
                    purpose = "media-preview",
                ),
            )
        } catch (e: IllegalAccessException) {
            return forbiddenResponse()
        } catch (e: AssetNotFoundException) {
            return ResponseEntity.notFound().build()
        }

        return when {
            !isReadyImage(response.mediaType, response.status) -> ResponseEntity.notFound().build()

            else -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(response.mediaType))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(PREVIEW_CACHE_MINUTES)))
                .body(downloadBody(assetId, response.storageKey, "media-preview"))
        }
    }

    @Operation(summary = "Stream signed asset content for image/video/pdf preview or download")
    @GetMapping("/{assetId}/content")
    @Suppress("SwallowedException")
    suspend fun contentAsset(
        @PathVariable assetId: String,
        @RequestParam workspaceId: String,
        @RequestParam expiresAt: Long,
        @RequestParam signature: String,
    ): ResponseEntity<Flux<DataBuffer>> {
        val response = try {
            mediator.send(
                StreamMediaAssetQuery(
                    assetId = assetId,
                    workspaceId = workspaceId,
                    expiresAt = expiresAt,
                    signature = signature,
                    purpose = "media-content",
                ),
            )
        } catch (e: IllegalAccessException) {
            return forbiddenResponse()
        } catch (e: AssetNotFoundException) {
            return ResponseEntity.notFound().build()
        }

        return when {
            response.status != MediaAssetStatus.READY -> ResponseEntity.notFound().build()

            else -> {
                val headers = HttpHeaders().apply {
                    contentType = MediaType.parseMediaType(response.mediaType)
                    cacheControl = CacheControl.maxAge(Duration.ofMinutes(PREVIEW_CACHE_MINUTES)).headerValue
                    contentDisposition = when {
                        response.mediaType.equals("application/pdf", ignoreCase = true) ->
                            ContentDisposition.inline().filename(response.originalFilename ?: "$assetId.pdf").build()

                        response.mediaType.startsWith("video/", ignoreCase = true) ->
                            ContentDisposition.inline().filename(response.originalFilename ?: "$assetId.mp4").build()

                        else ->
                            ContentDisposition.attachment().filename(response.originalFilename ?: assetId).build()
                    }
                }

                ResponseEntity.ok()
                    .headers(headers)
                    .body(downloadBody(assetId, response.storageKey, "media-content"))
            }
        }
    }

    private fun forbiddenResponse(): ResponseEntity<Flux<DataBuffer>> = ResponseEntity.status(HTTP_FORBIDDEN).build()

    private fun isReadyImage(mediaType: String, status: MediaAssetStatus): Boolean =
        status == MediaAssetStatus.READY && mediaType.startsWith("image/", ignoreCase = true)

    private suspend fun downloadBody(assetId: String, storageKey: String, purpose: String): Flux<DataBuffer> =
        storageApplicationService
            .download(
                bucket = mediaUploadSettings.storageBucket,
                key = storageKey,
                downloaderId = "$purpose:$assetId",
            )
            .asFlux()
}
