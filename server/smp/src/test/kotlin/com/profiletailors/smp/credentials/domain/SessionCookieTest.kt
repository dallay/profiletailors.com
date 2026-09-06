package com.profiletailors.smp.credentials.domain

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SessionCookieTest {

    @Test
    fun `SessionCookie accepts valid cookie`() {
        assertDoesNotThrow {
            SessionCookie(
                name = "session",
                value = "abc123",
                path = "/",
                sameSite = "Lax",
                secure = true,
                httpOnly = true,
                maxAgeSeconds = 3600L,
            )
        }
    }

    @Test
    fun `SessionCookie rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            SessionCookie(
                name = "",
                value = "abc123",
                path = "/",
                sameSite = "Lax",
                secure = true,
                httpOnly = true,
                maxAgeSeconds = 3600L,
            )
        }
    }

    @Test
    fun `SessionCookie rejects blank path`() {
        assertThrows<IllegalArgumentException> {
            SessionCookie(
                name = "session",
                value = "abc123",
                path = "   ",
                sameSite = "Lax",
                secure = true,
                httpOnly = true,
                maxAgeSeconds = 3600L,
            )
        }
    }
}
