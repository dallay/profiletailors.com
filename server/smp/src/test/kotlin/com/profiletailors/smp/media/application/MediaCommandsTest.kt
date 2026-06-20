package com.profiletailors.smp.media.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class MediaCommandsTest {
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
