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

    @Test
    fun `defaults to a standard message and no cause when constructed without arguments`() {
        val exception = InvalidSocialContentCursorException()

        exception.message shouldBe "Invalid social content cursor"
        exception.cause shouldBe null
    }

    @Test
    fun `is an illegal argument exception`() {
        val exception = InvalidSocialContentCursorException()

        (exception is IllegalArgumentException) shouldBe true
    }
}
