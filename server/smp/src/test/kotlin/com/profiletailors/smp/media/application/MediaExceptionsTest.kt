package com.profiletailors.smp.media.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaExceptionsTest {
    @Test
    fun `AssetNotReadyException should have correct message`() {
        val ex = AssetNotReadyException("id-1", "not-processed")
        assertEquals("Asset id-1 is not ready: not-processed", ex.message)
    }

    @Test
    fun `UploadConflictException should have correct message`() {
        val ex = UploadConflictException("id-1", "READY")
        assertEquals("Asset id-1 is already READY and cannot be re-uploaded.", ex.message)
    }

    @Test
    fun `RateLimitExceededException should have correct message`() {
        val ex = RateLimitExceededException("ws-1", "hourly_creations", 11, 10, 3600)
        assertEquals("Rate limit exceeded: hourly_creations (11/10)", ex.message)
        assertEquals(3600, ex.retryAfterSeconds)
    }

    @Test
    fun `FileTooLargeException should have correct message`() {
        val ex = FileTooLargeException(200, 100)
        assertEquals("File size 200 exceeds max 100", ex.message)
    }
}
