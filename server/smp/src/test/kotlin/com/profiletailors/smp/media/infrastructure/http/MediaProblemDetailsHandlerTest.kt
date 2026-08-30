package com.profiletailors.smp.media.infrastructure.http

import com.profiletailors.smp.media.application.AssetNotFoundException
import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.FileTooLargeException
import com.profiletailors.smp.media.application.InvalidCursorException
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.media.application.RateLimitExceededException
import com.profiletailors.smp.media.application.UnsupportedMediaTypeException
import com.profiletailors.smp.media.application.UploadConflictException
import com.profiletailors.smp.media.application.UploadInProgressException
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class MediaProblemDetailsHandlerTest {

    private val handler = MediaProblemDetailsHandler()

    @Test
    fun `AssetNotFoundException → 404 with errorCode ASSET_NOT_FOUND`() {
        val exception = AssetNotFoundException("asset-42")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.NOT_FOUND.value()
        result.detail shouldBe "Requested asset was not found."
        result.title shouldBe "Asset not found"
        result.properties?.get("errorCode") shouldBe "ASSET_NOT_FOUND"
        result.properties?.get("assetId").shouldBeNull()
    }

    @Test
    fun `UploadConflictException → 409 redacts status`() {
        val exception = UploadConflictException("asset-1", "READY")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.CONFLICT.value()
        result.detail shouldBe "The asset cannot be uploaded in its current state."
        result.title shouldBe "Upload conflict"
        result.properties?.get("errorCode") shouldBe "ASSET_UPLOAD_CONFLICT"
        result.properties?.get("currentStatus").shouldBeNull()
    }

    @Test
    fun `UploadInProgressException → 409 redacts status`() {
        val exception = UploadInProgressException("asset-2", "PROCESSING")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.CONFLICT.value()
        result.detail shouldBe "An upload is already in progress for this asset."
        result.title shouldBe "Upload in progress"
        result.properties?.get("errorCode") shouldBe "ASSET_UPLOAD_IN_PROGRESS"
        result.properties?.get("currentStatus").shouldBeNull()
    }

    @Test
    fun `AssetNotReadyException → 422 redacts reason`() {
        val exception = AssetNotReadyException("asset-3", "storage unavailable")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.UNPROCESSABLE_CONTENT.value()
        result.detail shouldBe "The asset is not ready for this operation."
        result.title shouldBe "Asset not ready"
        result.properties?.get("errorCode") shouldBe "ASSET_NOT_READY"
        result.properties?.get("reason").shouldBeNull()
    }

    @Test
    fun `UnsupportedMediaTypeException → 400 with declared and detected types`() {
        val exception = UnsupportedMediaTypeException(
            message = "Unsupported media type.",
            declaredType = "video/avi",
            detectedType = "application/x-msdownload",
        )
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.detail shouldBe "Unsupported media type."
        result.title shouldBe "Unsupported media type"
        result.properties?.get("errorCode") shouldBe "UNSUPPORTED_MEDIA_TYPE"
        result.properties?.get("declaredType") shouldBe "video/avi"
        result.properties?.get("detectedType") shouldBe "application/x-msdownload"
    }

    @Test
    fun `UnsupportedMediaTypeException omits null detectedType`() {
        val exception = UnsupportedMediaTypeException(
            message = "Unsupported media type.",
            declaredType = "application/zip",
            detectedType = null,
        )
        val result = handler.handle(exception)

        result.properties?.get("declaredType") shouldBe "application/zip"
        result.properties?.get("detectedType").shouldBeNull()
    }

    @Test
    fun `FileTooLargeException → 413 with size details`() {
        val exception = FileTooLargeException(600_000_000L, 500_000_000L)
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.CONTENT_TOO_LARGE.value()
        result.detail shouldBe "File size (600000000 bytes) exceeds the 500 MB limit."
        result.title shouldBe "File too large"
        result.properties?.get("errorCode") shouldBe "FILE_TOO_LARGE"
        result.properties?.get("actualSize") shouldBe 600_000_000L
        result.properties?.get("maxAllowed") shouldBe 500_000_000L
    }

    @Test
    fun `RateLimitExceededException → 429 ResponseEntity with Retry-After header`() {
        val exception = RateLimitExceededException(
            workspaceId = "ws-1",
            limitType = "hourly_creations",
            currentValue = 201,
            limitValue = 200,
            retryAfterSeconds = 3600,
        )
        val response = handler.handle(exception)

        val entity = response
        entity.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        entity.headers[HttpHeaders.RETRY_AFTER]?.first() shouldBe "3600"

        val body = entity.body.shouldNotBeNull()
        body.detail shouldBe "Rate limit exceeded. Please try again later."
        body.title shouldBe "Rate limit exceeded"
        body.properties?.get("errorCode") shouldBe "RATE_LIMIT_EXCEEDED"
        body.properties?.get("workspaceId").shouldBeNull()
        body.properties?.get("limitType").shouldBeNull()
        body.properties?.get("currentValue").shouldBeNull()
        body.properties?.get("limitValue").shouldBeNull()
        body.properties?.get("retryAfterSeconds") shouldBe 3600
    }

    @Test
    fun `InvalidCursorException → 400 with redacted message`() {
        val exception = InvalidCursorException("Cursor is malformed: invalid base64")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.detail shouldBe "Invalid pagination cursor."
        result.title shouldBe "Invalid cursor"
        result.properties?.get("errorCode") shouldBe "INVALID_CURSOR"
    }

    @Test
    fun `InvalidCursorException uses default detail when message is blank`() {
        val exception = InvalidCursorException("")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.detail shouldBe "Invalid pagination cursor."
    }

    @Test
    fun `MediaServiceUnavailableException → 503 with redacted message`() {
        val exception = MediaServiceUnavailableException("Storage gateway unreachable")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        result.detail shouldBe "Media service is temporarily unavailable."
        result.title shouldBe "Media service unavailable"
        result.properties?.get("errorCode") shouldBe "MEDIA_SERVICE_UNAVAILABLE"
    }

    @Test
    fun `MediaServiceUnavailableException uses default detail when message is blank`() {
        val exception = MediaServiceUnavailableException("")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        result.detail shouldBe "Media service is temporarily unavailable."
    }

    @Test
    fun `ResponseStatusException CONTENT_TOO_LARGE → FILE_TOO_LARGE code`() {
        val exception = ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "File too large")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.CONTENT_TOO_LARGE.value()
        result.detail shouldBe "File too large"
        result.title shouldBe "File too large"
        result.properties?.get("errorCode") shouldBe "FILE_TOO_LARGE"
    }

    @Test
    fun `ResponseStatusException falls back to status code value as title`() {
        val exception = ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.FORBIDDEN.value()
        result.detail shouldBe "Access denied"
        result.title shouldBe "403"
    }

    @Test
    fun `ResponseStatusException uses status code as detail when reason is null`() {
        val exception = ResponseStatusException(HttpStatus.BAD_GATEWAY, null)
        val result = handler.handle(exception)

        result.status shouldBe HttpStatus.BAD_GATEWAY.value()
        result.detail shouldBe "502"
    }
}
