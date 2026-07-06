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

    // ─── AssetNotFoundException ─────────────────────────────────────────────

    @Test
    fun `AssetNotFoundException → 404 with errorCode ASSET_NOT_FOUND`() {
        val exception = AssetNotFoundException("asset-42")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.NOT_FOUND.value(), result.status)
        assertEquals("Asset asset-42 not found.", result.detail)
        assertEquals("Asset not found", result.title)
        assertEquals("ASSET_NOT_FOUND", result.properties?.get("errorCode"))
        assertEquals("asset-42", result.properties?.get("assetId"))
    }

    // ─── UploadConflictException ─────────────────────────────────────────────

    @Test
    fun `UploadConflictException → 409 with currentStatus`() {
        val exception = UploadConflictException("asset-1", "READY")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), result.status)
        assertEquals("Asset asset-1 has already completed upload and cannot be re-uploaded.", result.detail)
        assertEquals("Upload conflict", result.title)
        assertEquals("ASSET_UPLOAD_CONFLICT", result.properties?.get("errorCode"))
        assertEquals("READY", result.properties?.get("currentStatus"))
    }

    // ─── UploadInProgressException ─────────────────────────────────────────

    @Test
    fun `UploadInProgressException → 409 with currentStatus`() {
        val exception = UploadInProgressException("asset-2", "PROCESSING")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.CONFLICT.value(), result.status)
        assertEquals("Asset asset-2 already has an upload in progress.", result.detail)
        assertEquals("Upload in progress", result.title)
        assertEquals("ASSET_UPLOAD_IN_PROGRESS", result.properties?.get("errorCode"))
        assertEquals("PROCESSING", result.properties?.get("currentStatus"))
    }

    // ─── AssetNotReadyException ─────────────────────────────────────────

    @Test
    fun `AssetNotReadyException → 422 with reason`() {
        val exception = AssetNotReadyException("asset-3", "storage unavailable")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), result.status)
        assertEquals("Asset asset-3 is not ready: storage unavailable", result.detail)
        assertEquals("Asset not ready", result.title)
        assertEquals("ASSET_NOT_READY", result.properties?.get("errorCode"))
        assertEquals("storage unavailable", result.properties?.get("reason"))
    }

    // ─── UnsupportedMediaTypeException ──────────────────────────────────

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

    // ─── FileTooLargeException ───────────────────────────────────────────

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

    // ─── RateLimitExceededException ────────────────────────────────────────

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
        assertEquals("Rate limit exceeded for hourly_creations.", body.detail)
        assertEquals("Rate limit exceeded", body.title)
        assertEquals("RATE_LIMIT_EXCEEDED", body.properties?.get("errorCode"))
        assertEquals("ws-1", body.properties?.get("workspaceId"))
        assertEquals("hourly_creations", body.properties?.get("limitType"))
        assertEquals(201, body.properties?.get("currentValue"))
        assertEquals(200, body.properties?.get("limitValue"))
        assertEquals(3600, body.properties?.get("retryAfterSeconds"))
    }

    // ─── InvalidCursorException ───────────────────────────────────────────

    @Test
    fun `InvalidCursorException → 400 with exception message`() {
        val exception = InvalidCursorException("Cursor is malformed: invalid base64")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.status)
        assertEquals("Cursor is malformed: invalid base64", result.detail)
        assertEquals("Invalid cursor", result.title)
        assertEquals("INVALID_CURSOR", result.properties?.get("errorCode"))
    }

    @Test
    fun `InvalidCursorException keeps empty message when provided`() {
        val exception = InvalidCursorException("")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.status)
        assertEquals("", result.detail)
    }

    // ─── MediaServiceUnavailableException ──────────────────────────────────

    @Test
    fun `MediaServiceUnavailableException → 503 with message`() {
        val exception = MediaServiceUnavailableException("Storage gateway unreachable")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.status)
        assertEquals("Storage gateway unreachable", result.detail)
        assertEquals("Media service unavailable", result.title)
        assertEquals("MEDIA_SERVICE_UNAVAILABLE", result.properties?.get("errorCode"))
    }

    @Test
    fun `MediaServiceUnavailableException keeps empty message when provided`() {
        val exception = MediaServiceUnavailableException("")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.status)
        assertEquals("", result.detail)
    }

    // ─── ResponseStatusException ──────────────────────────────────────────

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
