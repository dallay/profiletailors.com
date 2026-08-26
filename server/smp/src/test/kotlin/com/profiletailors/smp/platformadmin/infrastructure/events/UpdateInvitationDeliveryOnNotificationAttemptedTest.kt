package com.profiletailors.smp.platformadmin.infrastructure.events

import com.profiletailors.notifications.domain.event.InvitationDeliveryAttempted
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class UpdateInvitationDeliveryOnNotificationAttemptedTest {

    @Test
    fun `SENT outcome updates the invitation's deliveryStatus to SENT`() = runTest {
        val invitationId = UUID.randomUUID()
        val original = invitation(invitationId, InvitationDeliveryStatus.PENDING)
        val updatedSlot = slot<WaitlistInvitation>()

        val repository = mockk<WaitlistInvitationRepository>()
        coEvery { repository.findById(WaitlistInvitationId(invitationId)) } returns original
        coEvery { repository.update(capture(updatedSlot)) } answers { updatedSlot.captured }

        UpdateInvitationDeliveryOnNotificationAttempted(repository).consume(
            InvitationDeliveryAttempted(invitationId = invitationId, status = "SENT"),
        )

        assertThat(updatedSlot.captured.deliveryStatus).isEqualTo(InvitationDeliveryStatus.SENT)
    }

    @Test
    fun `FAILED outcome updates the invitation's deliveryStatus to FAILED`() = runTest {
        val invitationId = UUID.randomUUID()
        val original = invitation(invitationId, InvitationDeliveryStatus.PENDING)
        val updatedSlot = slot<WaitlistInvitation>()

        val repository = mockk<WaitlistInvitationRepository>()
        coEvery { repository.findById(WaitlistInvitationId(invitationId)) } returns original
        coEvery { repository.update(capture(updatedSlot)) } answers { updatedSlot.captured }

        UpdateInvitationDeliveryOnNotificationAttempted(repository).consume(
            InvitationDeliveryAttempted(invitationId = invitationId, status = "FAILED"),
        )

        assertThat(updatedSlot.captured.deliveryStatus).isEqualTo(InvitationDeliveryStatus.FAILED)
    }

    @Test
    fun `unknown status leaves the invitation untouched and does not call update`() = runTest {
        val invitationId = UUID.randomUUID()
        val repository = mockk<WaitlistInvitationRepository>()
        coEvery { repository.findById(WaitlistInvitationId(invitationId)) } returns
            invitation(invitationId, InvitationDeliveryStatus.PENDING)

        UpdateInvitationDeliveryOnNotificationAttempted(repository).consume(
            InvitationDeliveryAttempted(invitationId = invitationId, status = "WHATEVER"),
        )

        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `missing invitation is a no-op and does not throw`() = runTest {
        val invitationId = UUID.randomUUID()
        val repository = mockk<WaitlistInvitationRepository>()
        coEvery { repository.findById(WaitlistInvitationId(invitationId)) } returns null

        UpdateInvitationDeliveryOnNotificationAttempted(repository).consume(
            InvitationDeliveryAttempted(invitationId = invitationId, status = "SENT"),
        )

        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `idempotent re-delivery leaves the invitation at SENT (no exception)`() = runTest {
        val invitationId = UUID.randomUUID()
        val sent = invitation(invitationId, InvitationDeliveryStatus.SENT)
        val updatedSlot = slot<WaitlistInvitation>()

        val repository = mockk<WaitlistInvitationRepository>()
        coEvery { repository.findById(WaitlistInvitationId(invitationId)) } returns sent
        coEvery { repository.update(capture(updatedSlot)) } answers { updatedSlot.captured }

        UpdateInvitationDeliveryOnNotificationAttempted(repository).consume(
            InvitationDeliveryAttempted(invitationId = invitationId, status = "SENT"),
        )

        assertThat(updatedSlot.captured.deliveryStatus).isEqualTo(InvitationDeliveryStatus.SENT)
    }

    private fun invitation(id: UUID, deliveryStatus: InvitationDeliveryStatus): WaitlistInvitation = WaitlistInvitation(
        id = WaitlistInvitationId(id),
        waitlistEntryId = id.toString(),
        tokenHash = "hashed",
        status = WaitlistInvitationStatus.ACTIVE,
        issuedAt = Instant.parse("2026-08-24T00:00:00Z"),
        expiresAt = Instant.parse("2026-08-31T00:00:00Z"),
        createdBy = id,
        deliveryStatus = deliveryStatus,
    )
}
