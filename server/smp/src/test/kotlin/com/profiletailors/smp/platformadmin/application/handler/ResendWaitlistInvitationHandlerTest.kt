package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.notifications.domain.event.InvitationResent
import com.profiletailors.smp.platformadmin.application.command.ResendWaitlistInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationContext
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotResendableException
import com.profiletailors.smp.platformadmin.domain.InvitationRateLimitExceededException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ResendWaitlistInvitationHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val ttl = Duration.ofDays(7)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val entryId = "abcdef01-2345-6789-abcd-ef0123456789"

    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val eventPublisher = mockk<EventPublisher<DomainEvent>>()
    private val waitlistEntryAdmin = mockk<WaitlistEntryAdmin>()

    private val tokenHasher = object : TokenHasher {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
    }

    private val acceptUrlTemplate = AcceptUrlTemplate { rawToken ->
        "https://app.profiletailors.com/invitations/accept?token=$rawToken"
    }

    private val invitationContext = WaitlistInvitationContext(
        recipientEmail = "candidate@example.com",
        workspaceName = "Profile Tailors Beta",
        locale = "en",
    )

    private val handler = ResendWaitlistInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
        invitationTtl = ttl,
        resendLimit = 3,
        resendWindowHours = 24,
        tokenHasher = tokenHasher,
        eventPublisher = eventPublisher,
        acceptUrlTemplate = acceptUrlTemplate,
        waitlistEntryAdmin = waitlistEntryAdmin,
    )

    private val ownerRoles = setOf(PlatformRole.PLATFORM_OWNER)
    private val auditorRoles = setOf(PlatformRole.AUDITOR)

    @Test
    fun `throws PlatformAccessDeniedException when operator lacks resend permission`() = runTest {
        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command(roles = auditorRoles))
        }
    }

    @Test
    fun `throws InvitationNotFoundException when invitation does not exist`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns null
        assertThrows<InvitationNotFoundException> { handler.handle(command()) }
    }

    @Test
    fun `throws InvitationNotResendableException when invitation is not active`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns
            activeInvitation().copy(status = WaitlistInvitationStatus.REVOKED)
        assertThrows<InvitationNotResendableException> { handler.handle(command()) }
    }

    @Test
    fun `throws InvitationRateLimitExceededException when resend limit is reached`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns activeInvitation()
        coEvery { invitationRepository.countResendsSince(any(), any()) } returns 3

        assertThrows<InvitationRateLimitExceededException> { handler.handle(command()) }
    }

    @Test
    fun `resends invitation by superseding existing and saving new active invitation`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns activeInvitation()
        coEvery { invitationRepository.countResendsSince(any(), any()) } returns 0
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns invitationContext
        val supersededSlot = slot<WaitlistInvitation>()
        coEvery { invitationRepository.update(capture(supersededSlot)) } answers { supersededSlot.captured }
        val savedSlot = slot<WaitlistInvitation>()
        coEvery { invitationRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
        coEvery { eventPublisher.publish(any<DomainEvent>()) } answers { Unit }

        val result = handler.handle(command())

        assertNotNull(result.id)
        assertEquals(entryId, result.waitlistEntryId)
        assertEquals(WaitlistInvitationStatus.ACTIVE.name, result.status)
        assertEquals(InvitationDeliveryStatus.PENDING.name, result.deliveryStatus)
        assertEquals(clock.instant(), result.issuedAt)
        assertEquals(clock.instant() + ttl, result.expiresAt)

        assertThat(supersededSlot.captured.status).isEqualTo(WaitlistInvitationStatus.SUPERSEDED)
        val saved = savedSlot.captured
        assertThat(saved.waitlistEntryId).isEqualTo(entryId)
        assertThat(saved.status).isEqualTo(WaitlistInvitationStatus.ACTIVE)
        assertThat(saved.createdBy).isEqualTo(operatorId)
        assertThat(saved.tokenHash).startsWith("hashed-")
    }

    @Test
    fun `publishes INVITATION_RESENT audit event and InvitationResent domain event`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns activeInvitation()
        coEvery { invitationRepository.countResendsSince(any(), any()) } returns 0
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns invitationContext
        coEvery { invitationRepository.update(any()) } answers { firstArg() }
        coEvery { invitationRepository.save(any()) } answers { firstArg() }
        val auditSlot = slot<AdminAuditEvent>()
        coEvery { auditPublisher.publish(capture(auditSlot)) } answers { Unit }
        val eventSlot = slot<DomainEvent>()
        coEvery { eventPublisher.publish(capture(eventSlot)) } answers { Unit }

        handler.handle(command())

        assertThat(auditSlot.captured.action).isEqualTo(AdminAuditAction.INVITATION_RESENT)
        assertThat(auditSlot.captured.targetId).isEqualTo(invitationId.toString())
        assertThat(auditSlot.captured.operatorPrincipalId).isEqualTo(operatorId)
        assertThat(eventSlot.captured).isInstanceOf(InvitationResent::class.java)
        val published = eventSlot.captured as InvitationResent
        assertThat(published.previousInvitationId).isEqualTo(invitationId)
    }

    private fun command(roles: Set<PlatformRole> = ownerRoles) = ResendWaitlistInvitationCommand(
        operatorPrincipalId = operatorId,
        operatorRoles = roles,
        invitationId = invitationId,
    )

    private fun activeInvitation() = WaitlistInvitation(
        id = WaitlistInvitationId(invitationId),
        waitlistEntryId = entryId,
        tokenHash = "existing-hash",
        status = WaitlistInvitationStatus.ACTIVE,
        issuedAt = clock.instant().minusSeconds(3600),
        expiresAt = clock.instant().plusSeconds(604_800),
        createdBy = operatorId,
        deliveryStatus = InvitationDeliveryStatus.SENT,
        deliveryAttemptCount = 1,
    )
}
