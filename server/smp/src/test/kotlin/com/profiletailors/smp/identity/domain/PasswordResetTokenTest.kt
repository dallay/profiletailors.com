package com.profiletailors.smp.identity.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PasswordResetTokenTest {

    private val tokenId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val principalId = "user-1"
    private val requestedAt: Instant = Instant.parse("2026-07-27T12:00:00Z")
    private val expiresAt: Instant = Instant.parse("2026-07-27T12:30:00Z")

    @Test
    fun `isExpired returns true when now is at or after expiry`() {
        val token = PasswordResetToken(
            id = tokenId,
            principalId = principalId,
            tokenHash = "hash",
            requestedAt = requestedAt,
            expiresAt = expiresAt,
        )

        assertTrue(token.isExpired(expiresAt))
        assertTrue(token.isExpired(expiresAt.plusSeconds(1)))
    }

    @Test
    fun `isExpired returns false when now is strictly before expiry`() {
        val token = PasswordResetToken(
            id = tokenId,
            principalId = principalId,
            tokenHash = "hash",
            requestedAt = requestedAt,
            expiresAt = expiresAt,
        )

        assertFalse(token.isExpired(expiresAt.minusSeconds(1)))
    }

    @Test
    fun `isUsed returns true when usedAt is non-null`() {
        val token = PasswordResetToken(
            id = tokenId,
            principalId = principalId,
            tokenHash = "hash",
            requestedAt = requestedAt,
            expiresAt = expiresAt,
            usedAt = Instant.parse("2026-07-27T12:10:00Z"),
        )

        assertTrue(token.isUsed())
    }

    @Test
    fun `isUsed returns false when usedAt is null`() {
        val token = PasswordResetToken(
            id = tokenId,
            principalId = principalId,
            tokenHash = "hash",
            requestedAt = requestedAt,
            expiresAt = expiresAt,
        )

        assertFalse(token.isUsed())
    }

    @Test
    fun `isValid returns true only when token is neither expired nor used`() {
        val fresh = PasswordResetToken(
            id = tokenId,
            principalId = principalId,
            tokenHash = "hash",
            requestedAt = requestedAt,
            expiresAt = expiresAt,
        )
        assertTrue(fresh.isValid(expiresAt.minusSeconds(1)))

        val expired = fresh.copy()
        assertFalse(expired.isValid(expiresAt))

        val used = fresh.copy(usedAt = Instant.parse("2026-07-27T12:10:00Z"))
        assertFalse(used.isValid(expiresAt.minusSeconds(1)))
    }
}
