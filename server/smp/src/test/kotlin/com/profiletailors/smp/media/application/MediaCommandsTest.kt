package com.profiletailors.smp.media.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class MediaCommandsTest {
    @Test
    fun `upload asset command provides sensible defaults`() {
        val command = UploadAssetCommand(
            assetId = "asset-1",
            workspaceId = "ws-1",
            fileStream = kotlinx.coroutines.flow.emptyFlow(),
            contentLength = null,
        )

        assertEquals("asset-1", command.assetId)
        assertEquals("ws-1", command.workspaceId)
        assertEquals(500L * 1024 * 1024, command.maxFileSizeBytes)
        assertEquals(10L * 60L, command.timeoutSeconds)
        assertEquals(null, command.contentType)
    }

    @Test
    fun `media exceptions expose their domain context`() {
        val notReady = AssetNotReadyException("asset-1", "processing")
        assertEquals("asset-1", notReady.assetId)
        assertEquals("processing", notReady.reason)

        val unavailable = MediaServiceUnavailableException("service down")
        assertEquals("service down", unavailable.message)

        val unsupported = UnsupportedMediaTypeException(
            message = "bad type",
            declaredType = "image/bmp",
            detectedType = "application/octet-stream",
        )
        assertEquals("image/bmp", unsupported.declaredType)
        assertEquals("application/octet-stream", unsupported.detectedType)

        val conflict = UploadConflictException("asset-2", "READY")
        assertEquals("asset-2", conflict.assetId)
        assertEquals("READY", conflict.currentStatus)

        val inProgress = UploadInProgressException("asset-3", "PROCESSING")
        assertEquals("asset-3", inProgress.assetId)
        assertEquals("PROCESSING", inProgress.currentStatus)

        val rateLimit = RateLimitExceededException("ws-1", "hourly_creations", 3, 2, 120)
        assertEquals("ws-1", rateLimit.workspaceId)
        assertEquals("hourly_creations", rateLimit.limitType)
        assertEquals(3, rateLimit.currentValue)
        assertEquals(2, rateLimit.limitValue)
        assertEquals(120, rateLimit.retryAfterSeconds)

        val invalidCursor = InvalidCursorException("bad cursor")
        assertEquals("bad cursor", invalidCursor.message)

        val notFound = AssetNotFoundException("asset-4")
        assertEquals("asset-4", notFound.assetId)

        val fileTooLarge = FileTooLargeException(1025, 1024)
        assertEquals(1025, fileTooLarge.actualSize)
        assertEquals(1024, fileTooLarge.maxAllowed)
    }

    @Test
    fun `media upload settings require positive limits and non blank bucket`() {
        val concurrentError = assertThrows<IllegalArgumentException> {
            MediaUploadSettings(
                maxConcurrentUploads = 0,
                maxCreationsPerHour = 10,
                storageBucket = "attachments",
            )
        }
        assertEquals("media.max-concurrent-uploads must be greater than zero", concurrentError.message)

        val creationsError = assertThrows<IllegalArgumentException> {
            MediaUploadSettings(
                maxConcurrentUploads = 5,
                maxCreationsPerHour = 0,
                storageBucket = "attachments",
            )
        }
        assertEquals("media.max-creations-per-hour must be greater than zero", creationsError.message)

        val bucketError = assertThrows<IllegalArgumentException> {
            MediaUploadSettings(
                maxConcurrentUploads = 5,
                maxCreationsPerHour = 10,
                storageBucket = " ",
            )
        }
        assertEquals("media.storage.bucket must not be blank", bucketError.message)
    }

    @Test
    fun `media reconciler settings require positive thresholds and non blank bucket`() {
        val bucketError = assertThrows<IllegalArgumentException> {
            MediaReconcilerSettings(
                storageBucket = "",
                staleThresholdHours = 2,
                gracePeriodMinutes = 30,
            )
        }
        assertEquals("media.storage.bucket must not be blank", bucketError.message)

        val staleError = assertThrows<IllegalArgumentException> {
            MediaReconcilerSettings(
                storageBucket = "attachments",
                staleThresholdHours = 0,
                gracePeriodMinutes = 30,
            )
        }
        assertEquals("media.stale.threshold-hours must be greater than zero", staleError.message)

        val graceError = assertThrows<IllegalArgumentException> {
            MediaReconcilerSettings(
                storageBucket = "attachments",
                staleThresholdHours = 2,
                gracePeriodMinutes = 0,
            )
        }
        assertEquals("media.stale.grace-period-minutes must be greater than zero", graceError.message)
    }
}
