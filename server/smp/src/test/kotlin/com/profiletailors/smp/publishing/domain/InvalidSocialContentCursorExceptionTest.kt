package com.profiletailors.smp.publishing.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InvalidSocialContentCursorExceptionTest {
    @Test
    fun `preserves message and cause`() {
        val cause = IllegalArgumentException("underlying cause")
        val exception = InvalidSocialContentCursorException("invalid calendar cursor", cause)

        exception.message shouldBe "invalid calendar cursor"
        exception.cause shouldBe cause
    }
}
