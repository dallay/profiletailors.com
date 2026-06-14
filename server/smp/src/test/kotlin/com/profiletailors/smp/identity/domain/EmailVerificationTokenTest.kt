package com.profiletailors.smp.identity.domain

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmailVerificationTokenTest {

    private val validToken = EmailVerificationToken(
        email = "test@example.com",
        tokenHash = "abc123",
        expiresAt = Instant.parse("2026-05-21T10:00:00Z"),
    )

    @Test
    fun `token is valid before expiry and when not used`() {
        val now = Instant.parse("2026-05-20T10:00:00Z")
        assertTrue(validToken.isValid(now))
    }

    @Test
    fun `token is invalid after expiry`() {
        val now = Instant.parse("2026-05-22T10:00:00Z")
        assertFalse(validToken.isValid(now))
    }

    @Test
    fun `token is invalid when already used`() {
        val usedToken = validToken.copy(usedAt = Instant.parse("2026-05-20T12:00:00Z"))
        val now = Instant.parse("2026-05-20T14:00:00Z")
        assertFalse(usedToken.isValid(now))
    }

    @Test
    fun `token is invalid when used and after expiry`() {
        val usedExpiredToken = validToken.copy(
            usedAt = Instant.parse("2026-05-20T12:00:00Z"),
            expiresAt = Instant.parse("2026-05-21T10:00:00Z"),
        )
        val now = Instant.parse("2026-05-22T10:00:00Z")
        assertFalse(usedExpiredToken.isValid(now))
    }

    @Test
    fun `token is valid at exact expiry boundary (before expiry instant)`() {
        val now = Instant.parse("2026-05-21T09:59:59Z")
        assertTrue(validToken.isValid(now))
    }

    @Test
    fun `token is invalid at exact expiry instant`() {
        val now = Instant.parse("2026-05-21T10:00:00Z")
        assertFalse(validToken.isValid(now))
    }
}
