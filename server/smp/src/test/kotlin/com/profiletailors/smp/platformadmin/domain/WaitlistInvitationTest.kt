package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class WaitlistInvitationTest {

    private val now = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val entryId = "entry-abc-123"

    private fun activeInvitation() = WaitlistInvitation(
        id = WaitlistInvitationId.generate(),
        waitlistEntryId = entryId,
        tokenHash = "hash",
        status = WaitlistInvitationStatus.ACTIVE,
        issuedAt = now,
        expiresAt = now.plusSeconds(604_800),
        createdBy = operatorId,
        deliveryStatus = InvitationDeliveryStatus.PENDING,
    )

    @Test
    fun `active invitation isActive true`() {
        assertTrue(activeInvitation().isActive(now))
    }

    @Test
    fun `revoked invitation isActive false`() {
        assertFalse(activeInvitation().revoke(now, operatorId).isActive(now))
    }

    @Test
    fun `expired active invitation isActive false`() {
        assertFalse(activeInvitation().isActive(activeInvitation().expiresAt.plusSeconds(1)))
    }

    @Test
    fun `invitation is not expired before expiresAt`() {
        assertFalse(activeInvitation().isExpired(now.plusSeconds(604_799)))
    }

    @Test
    fun `invitation is expired at exactly expiresAt`() {
        val inv = activeInvitation()
        assertTrue(inv.isExpired(inv.expiresAt))
    }

    @Test
    fun `invitation is expired after expiresAt`() {
        val inv = activeInvitation()
        assertTrue(inv.isExpired(inv.expiresAt.plusSeconds(1)))
    }

    @Test
    fun `revoke sets revokedAt and revokedBy`() {
        val revokedAt = now.plusSeconds(60)
        val revoked = activeInvitation().revoke(revokedAt, operatorId)
        assertEquals(WaitlistInvitationStatus.REVOKED, revoked.status)
        assertEquals(revokedAt, revoked.revokedAt)
        assertEquals(operatorId, revoked.revokedBy)
    }

    @Test
    fun `revoking non-active invitation throws`() {
        val revoked = activeInvitation().revoke(now, operatorId)
        assertThrows<InvitationNotRevocableException> { revoked.revoke(now, operatorId) }
    }

    @Test
    fun `supersede sets status to SUPERSEDED`() {
        val superseded = activeInvitation().supersede()
        assertEquals(WaitlistInvitationStatus.SUPERSEDED, superseded.status)
    }

    @Test
    fun `superseding non-active invitation throws`() {
        val superseded = activeInvitation().supersede()
        assertThrows<InvitationNotResendableException> { superseded.supersede() }
    }

    @Test
    fun `accept sets status to ACCEPTED with acceptedAt`() {
        val acceptedAt = now.plusSeconds(30)
        val accepted = activeInvitation().accept(acceptedAt)
        assertEquals(WaitlistInvitationStatus.ACCEPTED, accepted.status)
        assertEquals(acceptedAt, accepted.acceptedAt)
    }

    @Test
    fun `accepting non-active invitation throws`() {
        val revoked = activeInvitation().revoke(now, operatorId)
        assertThrows<InvitationNotAcceptableException> { revoked.accept(now) }
    }

    @Test
    fun `accepting expired invitation throws`() {
        val expired = activeInvitation()
        assertThrows<InvitationNotAcceptableException> { expired.accept(expired.expiresAt.plusSeconds(1)) }
    }
}
