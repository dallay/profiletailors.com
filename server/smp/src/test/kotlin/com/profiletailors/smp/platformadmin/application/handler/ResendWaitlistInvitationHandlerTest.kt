package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.ResendWaitlistInvitationCommand
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
    private val entryId = "entry-abc-123"

    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)

    private val tokenHasher = object : TokenHasher {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
    }

    private val handler = ResendWaitlistInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
        invitationTtl = ttl,
        resendLimit = 3,
        resendWindowHours = 24,
        tokenHasher = tokenHasher,
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
        coEvery { invitationRepository.update(any()) } answers { firstArg() }
        coEvery { invitationRepository.save(any()) } answers { firstArg() }

        val result = handler.handle(command())

        assertNotNull(result.id)
        assertEquals(entryId, result.waitlistEntryId)
        assertEquals(WaitlistInvitationStatus.ACTIVE.name, result.status)
        assertEquals(InvitationDeliveryStatus.PENDING.name, result.deliveryStatus)
        assertEquals(clock.instant(), result.issuedAt)
        assertEquals(clock.instant() + ttl, result.expiresAt)

        coVerify { invitationRepository.update(match { it.status == WaitlistInvitationStatus.SUPERSEDED }) }
        coVerify {
            invitationRepository.save(
                match {
                    it.waitlistEntryId == entryId &&
                        it.status == WaitlistInvitationStatus.ACTIVE &&
                        it.createdBy == operatorId &&
                        it.tokenHash.startsWith("hashed-")
                },
            )
        }
    }

    @Test
    fun `publishes INVITATION_RESENT audit event after successful resend`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns activeInvitation()
        coEvery { invitationRepository.countResendsSince(any(), any()) } returns 0
        coEvery { invitationRepository.update(any()) } answers { firstArg() }
        coEvery { invitationRepository.save(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify {
            auditPublisher.publish(
                match { event ->
                    event.action == AdminAuditAction.INVITATION_RESENT &&
                        event.targetId == invitationId.toString() &&
                        event.operatorPrincipalId == operatorId &&
                        event.result.name == "SUCCEEDED"
                },
            )
        }
        coVerify(exactly = 1) { auditPublisher.publish(any<AdminAuditEvent>()) }
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
