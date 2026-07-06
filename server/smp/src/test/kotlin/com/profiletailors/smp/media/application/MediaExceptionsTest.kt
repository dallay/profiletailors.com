package com.profiletailors.smp.media.application

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MediaExceptionsTest {
    @Test
    fun `AssetNotReadyException should have correct message`() {
        val ex = AssetNotReadyException("id-1", "not-processed")
        ex.message shouldBe "Asset id-1 is not ready: not-processed"
    }

    @Test
    fun `UploadConflictException should have correct message`() {
        val ex = UploadConflictException("id-1", "READY")
        ex.message shouldBe "Asset id-1 is already READY and cannot be re-uploaded."
    }

    @Test
    fun `RateLimitExceededException should have correct message`() {
        val ex = RateLimitExceededException("ws-1", "hourly_creations", 11, 10, 3600)
        ex.message shouldBe "Rate limit exceeded: hourly_creations (11/10)"
        ex.retryAfterSeconds shouldBe 3600
    }

    @Test
    fun `FileTooLargeException should have correct message`() {
        val ex = FileTooLargeException(200, 100)
        ex.message shouldBe "File size 200 exceeds max 100"
    }
}
