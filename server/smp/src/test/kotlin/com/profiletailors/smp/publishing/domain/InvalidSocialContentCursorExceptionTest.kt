package com.profiletailors.smp.publishing.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InvalidSocialContentCursorExceptionTest {
    @Test
    fun `preserves message and cause`() {
        val cause = IllegalArgumentException("underlying cause")
        val exception = InvalidSocialContentCursorException("invalid calendar cursor", cause)

        assertEquals("invalid calendar cursor", exception.message)
        assertEquals(cause, exception.cause)
    }
}
