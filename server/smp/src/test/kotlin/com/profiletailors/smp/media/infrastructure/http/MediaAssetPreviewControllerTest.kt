package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.smp.media.application.MediaAssetPreviewPort
import com.profiletailors.smp.media.application.MediaPreviewTokenService
import com.profiletailors.smp.media.application.MediaUploadSettings
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant

class MediaAssetPreviewControllerTest {

    private val uploadSettings = MediaUploadSettings(
        maxConcurrentUploads = 5,
        maxCreationsPerHour = 200,
        storageBucket = "attachments",
    )

    private val tokenService = MediaPreviewTokenService(
        signingSecret = "test-secret",
        previewUrlExpirySeconds = 3_600,
    )

    @Test
    fun `previewAsset returns 403 when signature is invalid`() = runTest {
        val controller = controller(asset = null)

        val response = controller.previewAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = Instant.now().epochSecond + 3_600,
            signature = "invalid-signature",
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `previewAsset returns 404 when ready asset is not an image`() = runTest {
        val controller = controller(
            asset = mediaAsset(status = MediaAssetStatus.READY, mediaType = "video/mp4"),
        )
        val token = signedToken("asset-1", "ws-1")

        val response = controller.previewAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = token.expiresAt,
            signature = token.signature,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `previewAsset returns 200 for ready image asset`() = runTest {
        val controller = controller(
            asset = mediaAsset(status = MediaAssetStatus.READY, mediaType = "image/png"),
        )
        val token = signedToken("asset-1", "ws-1")

        val response = controller.previewAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = token.expiresAt,
            signature = token.signature,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("image/png", response.headers.contentType!!.toString())
        assertTrue(!response.headers.cacheControl.isNullOrBlank())
    }

    @Test
    fun `contentAsset returns 403 when signature is invalid`() = runTest {
        val controller = controller(asset = null)

        val response = controller.contentAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = Instant.now().epochSecond + 3_600,
            signature = "bad-signature",
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `contentAsset returns 404 when asset is not ready`() = runTest {
        val controller = controller(
            asset = mediaAsset(status = MediaAssetStatus.PROCESSING, mediaType = "image/jpeg"),
        )
        val token = signedToken("asset-1", "ws-1")

        val response = controller.contentAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = token.expiresAt,
            signature = token.signature,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `contentAsset sets inline disposition for pdf`() = runTest {
        val controller = controller(
            asset = mediaAsset(
                status = MediaAssetStatus.READY,
                mediaType = "application/pdf",
                originalFilename = "report.pdf",
            ),
        )
        val token = signedToken("asset-1", "ws-1")

        val response = controller.contentAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = token.expiresAt,
            signature = token.signature,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.headers.getFirst("Content-Disposition")!!.startsWith("inline;"))
    }

    @Test
    fun `contentAsset sets inline disposition for video`() = runTest {
        val controller = controller(
            asset = mediaAsset(status = MediaAssetStatus.READY, mediaType = "video/mp4", originalFilename = "clip.mp4"),
        )
        val token = signedToken("asset-1", "ws-1")

        val response = controller.contentAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = token.expiresAt,
            signature = token.signature,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.headers.getFirst("Content-Disposition")!!.startsWith("inline;"))
    }

    @Test
    fun `contentAsset sets attachment disposition for generic files`() = runTest {
        val controller = controller(
            asset = mediaAsset(
                status = MediaAssetStatus.READY,
                mediaType = "application/octet-stream",
                originalFilename = "data.bin",
            ),
        )
        val token = signedToken("asset-1", "ws-1")

        val response = controller.contentAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            expiresAt = token.expiresAt,
            signature = token.signature,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.headers.getFirst("Content-Disposition")!!.startsWith("attachment;"))
    }

    private fun controller(asset: MediaAsset?): MediaAssetPreviewController {
        val previewPort = object : MediaAssetPreviewPort {
            override suspend fun findAsset(workspaceId: String, assetId: String): MediaAsset? = asset
            override fun download(bucket: String, key: String, downloaderId: String): Flow<ByteArray> =
                flowOf("preview".toByteArray())
        }

        return MediaAssetPreviewController(
            mediaAssetPreviewPort = previewPort,
            mediaPreviewTokenService = tokenService,
            mediaUploadSettings = uploadSettings,
        )
    }

    private fun mediaAsset(status: MediaAssetStatus, mediaType: String, originalFilename: String? = "asset.bin") =
        MediaAsset(
            assetId = "asset-1",
            workspaceId = "ws-1",
            sourceType = MediaSourceType.UPLOADED,
            fileHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 64-char hex
            mediaType = mediaType,
            storageKey = "assets/ws-1/asset-1",
            originalFilename = originalFilename,
            fileSizeBytes = if (status == MediaAssetStatus.READY) 1024L else null,
            status = status,
            createdAt = Instant.now(),
        )

    private data class Token(val expiresAt: Long, val signature: String)

    private fun signedToken(assetId: String, workspaceId: String): Token {
        val expiresAt = Instant.now().epochSecond + 3_600
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKey = javax.crypto.spec.SecretKeySpec("test-secret".toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val payload = "$assetId:$workspaceId:$expiresAt".toByteArray(Charsets.UTF_8)
        val signature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload))
        return Token(expiresAt, signature)
    }
}
