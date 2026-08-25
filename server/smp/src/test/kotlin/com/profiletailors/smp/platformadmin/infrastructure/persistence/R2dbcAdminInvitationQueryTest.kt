package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class R2dbcAdminInvitationQueryTest {

    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val query = R2dbcAdminInvitationQuery(invitationRepository)

    @Test
    fun `findById returns summary when invitation exists`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = WaitlistInvitation(
            id = WaitlistInvitationId(invitationId),
            waitlistEntryId = "entry-1",
            tokenHash = "hash",
            status = WaitlistInvitationStatus.ACTIVE,
            issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-02-01T00:00:00Z"),
            createdBy = UUID.randomUUID(),
            deliveryStatus = InvitationDeliveryStatus.SENT,
            deliveryAttemptCount = 2,
            version = 3,
        )
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation

        val result = query.findById(invitationId)

        assertNotNull(result)
        assertEquals(invitationId, result.id)
        assertEquals("entry-1", result.waitlistEntryId)
        assertEquals("ACTIVE", result.status)
        assertEquals("SENT", result.deliveryStatus)
        assertEquals(2, result.deliveryAttemptCount)
        assertEquals(3, result.version)
    }

    @Test
    fun `findById returns null when invitation does not exist`() = runTest {
        val invitationId = UUID.randomUUID()
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns null

        val result = query.findById(invitationId)

        assertNull(result)
    }

    @Test
    fun `findById maps accepted invitation with acceptedAt`() = runTest {
        val invitationId = UUID.randomUUID()
        val acceptedAt = Instant.parse("2026-01-15T10:00:00Z")
        val invitation = WaitlistInvitation(
            id = WaitlistInvitationId(invitationId),
            waitlistEntryId = "entry-2",
            tokenHash = "hash",
            status = WaitlistInvitationStatus.ACCEPTED,
            issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-02-01T00:00:00Z"),
            acceptedAt = acceptedAt,
            createdBy = UUID.randomUUID(),
            deliveryStatus = InvitationDeliveryStatus.SENT,
            version = 1,
        )
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation

        val result = query.findById(invitationId)

        assertNotNull(result)
        assertEquals(acceptedAt, result.acceptedAt)
        assertEquals("ACCEPTED", result.status)
    }

    @Test
    fun `findById maps revoked invitation with revokedAt and revokedBy`() = runTest {
        val invitationId = UUID.randomUUID()
        val revokedBy = UUID.randomUUID()
        val revokedAt = Instant.parse("2026-01-20T12:00:00Z")
        val invitation = WaitlistInvitation(
            id = WaitlistInvitationId(invitationId),
            waitlistEntryId = "entry-3",
            tokenHash = "hash",
            status = WaitlistInvitationStatus.REVOKED,
            issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-02-01T00:00:00Z"),
            revokedAt = revokedAt,
            revokedBy = revokedBy,
            createdBy = UUID.randomUUID(),
            deliveryStatus = InvitationDeliveryStatus.FAILED,
            version = 2,
        )
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation

        val result = query.findById(invitationId)

        assertNotNull(result)
        assertEquals(revokedAt, result.revokedAt)
        assertEquals(revokedBy, result.revokedBy)
        assertEquals("REVOKED", result.status)
        assertEquals("FAILED", result.deliveryStatus)
    }
}
