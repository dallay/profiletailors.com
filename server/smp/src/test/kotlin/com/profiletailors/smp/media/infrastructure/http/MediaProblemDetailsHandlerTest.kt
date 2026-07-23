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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        assertEquals(HttpStatus.NOT_FOUND.value(), result.status)
        assertEquals("Requested asset was not found.", result.detail)
        assertEquals("Asset not found", result.title)
        assertEquals("ASSET_NOT_FOUND", result.properties?.get("errorCode"))
        assertNull(result.properties?.get("assetId"))
    }

    @Test
    fun `UploadConflictException → 409 with currentStatus`() {
        val exception = UploadConflictException("asset-1", "READY")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), result.status)
        assertEquals("The asset cannot be uploaded in its current state.", result.detail)
        assertEquals("Upload conflict", result.title)
        assertEquals("ASSET_UPLOAD_CONFLICT", result.properties?.get("errorCode"))
        assertNull(result.properties?.get("currentStatus"))
    }

    @Test
    fun `UploadInProgressException → 409 with currentStatus`() {
        val exception = UploadInProgressException("asset-2", "PROCESSING")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), result.status)
        assertEquals("An upload is already in progress for this asset.", result.detail)
        assertEquals("Upload in progress", result.title)
        assertEquals("ASSET_UPLOAD_IN_PROGRESS", result.properties?.get("errorCode"))
        assertNull(result.properties?.get("currentStatus"))
    }

    @Test
    fun `AssetNotReadyException → 422 with reason`() {
        val exception = AssetNotReadyException("asset-3", "storage unavailable")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), result.status)
        assertEquals("The asset is not ready for this operation.", result.detail)
        assertEquals("Asset not ready", result.title)
        assertEquals("ASSET_NOT_READY", result.properties?.get("errorCode"))
        assertNull(result.properties?.get("reason"))
    }

    @Test
    fun `UnsupportedMediaTypeException → 400 with declared and detected types`() {
        val exception = UnsupportedMediaTypeException(
            message = "Unsupported media type.",
            declaredType = "video/avi",
            detectedType = "application/x-msdownload",
        )
        val result = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.status)
        assertEquals("Unsupported media type.", result.detail)
        assertEquals("Unsupported media type", result.title)
        assertEquals("UNSUPPORTED_MEDIA_TYPE", result.properties?.get("errorCode"))
        assertEquals("video/avi", result.properties?.get("declaredType"))
        assertEquals("application/x-msdownload", result.properties?.get("detectedType"))
    }

    @Test
    fun `UnsupportedMediaTypeException omits null detectedType`() {
        val exception = UnsupportedMediaTypeException(
            message = "Unsupported media type.",
            declaredType = "application/zip",
            detectedType = null,
        )
        val result = handler.handle(exception)

        assertEquals("application/zip", result.properties?.get("declaredType"))
        assertNull(result.properties?.get("detectedType"))
    }

    @Test
    fun `FileTooLargeException → 413 with size details`() {
        val exception = FileTooLargeException(600_000_000L, 500_000_000L)
        val result = handler.handle(exception)

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), result.status)
        assertEquals("File size (600000000 bytes) exceeds the 500 MB limit.", result.detail)
        assertEquals("File too large", result.title)
        assertEquals("FILE_TOO_LARGE", result.properties?.get("errorCode"))
        assertEquals(600_000_000L, result.properties?.get("actualSize"))
        assertEquals(500_000_000L, result.properties?.get("maxAllowed"))
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
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, entity.statusCode)
        assertEquals("3600", entity.headers[HttpHeaders.RETRY_AFTER]?.first())

        val body = entity.body!!
        assertEquals("Rate limit exceeded. Please try again later.", body.detail)
        assertEquals("Rate limit exceeded", body.title)
        assertEquals("RATE_LIMIT_EXCEEDED", body.properties?.get("errorCode"))
        assertNull(body.properties?.get("workspaceId"))
        assertNull(body.properties?.get("limitType"))
        assertNull(body.properties?.get("currentValue"))
        assertNull(body.properties?.get("limitValue"))
        assertEquals(3600, body.properties?.get("retryAfterSeconds"))
    }

    @Test
    fun `InvalidCursorException → 400 with exception message`() {
        val exception = InvalidCursorException("Cursor is malformed: invalid base64")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.status)
        assertEquals("Invalid pagination cursor.", result.detail)
        assertEquals("Invalid cursor", result.title)
        assertEquals("INVALID_CURSOR", result.properties?.get("errorCode"))
    }

    @Test
    fun `InvalidCursorException keeps empty message when provided`() {
        val exception = InvalidCursorException("")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.status)
        assertEquals("Invalid pagination cursor.", result.detail)
    }

    @Test
    fun `MediaServiceUnavailableException → 503 with message`() {
        val exception = MediaServiceUnavailableException("Storage gateway unreachable")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.status)
        assertEquals("Media service is temporarily unavailable.", result.detail)
        assertEquals("Media service unavailable", result.title)
        assertEquals("MEDIA_SERVICE_UNAVAILABLE", result.properties?.get("errorCode"))
    }

    @Test
    fun `MediaServiceUnavailableException keeps empty message when provided`() {
        val exception = MediaServiceUnavailableException("")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.status)
        assertEquals("Media service is temporarily unavailable.", result.detail)
    }

    @Test
    fun `ResponseStatusException PAYLOAD_TOO_LARGE → FILE_TOO_LARGE code`() {
        val exception = ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), result.status)
        assertEquals("File too large", result.detail)
        assertEquals("File too large", result.title)
        assertEquals("FILE_TOO_LARGE", result.properties?.get("errorCode"))
    }

    @Test
    fun `ResponseStatusException falls back to status code value as title`() {
        val exception = ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.FORBIDDEN.value(), result.status)
        assertEquals("Access denied", result.detail)
        assertEquals("403", result.title)
    }

    @Test
    fun `ResponseStatusException uses status code as detail when reason is null`() {
        val exception = ResponseStatusException(HttpStatus.BAD_GATEWAY, null)
        val result = handler.handle(exception)

        assertEquals(HttpStatus.BAD_GATEWAY.value(), result.status)
        assertEquals("502", result.detail)
    }
}
