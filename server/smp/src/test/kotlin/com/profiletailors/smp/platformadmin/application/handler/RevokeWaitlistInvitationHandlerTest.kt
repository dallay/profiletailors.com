package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.RevokeWaitlistInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotRevocableException
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
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RevokeWaitlistInvitationHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val entryId = "entry-abc-123"

    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)

    private val handler = RevokeWaitlistInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
    )

    private val ownerRoles = setOf(PlatformRole.PLATFORM_OWNER)
    private val auditorRoles = setOf(PlatformRole.AUDITOR)

    @Test
    fun `throws PlatformAccessDeniedException when operator lacks revoke permission`() = runTest {
        assertThrows<PlatformAccessDeniedException> { handler.handle(command(roles = auditorRoles)) }
    }

    @Test
    fun `throws InvitationNotFoundException when invitation does not exist`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns null
        assertThrows<InvitationNotFoundException> { handler.handle(command()) }
    }

    @Test
    fun `throws InvitationNotRevocableException when invitation is not active`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns
            invitation(status = WaitlistInvitationStatus.REVOKED)
        assertThrows<InvitationNotRevocableException> { handler.handle(command()) }
    }

    @Test
    fun `revokes active invitation and publishes audit event`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation()
        coEvery { invitationRepository.update(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify {
            invitationRepository.update(
                match { updated ->
                    updated.status == WaitlistInvitationStatus.REVOKED &&
                        updated.revokedAt == clock.instant() &&
                        updated.revokedBy == operatorId
                },
            )
        }
        coVerify {
            auditPublisher.publish(
                match { event ->
                    event.action == AdminAuditAction.INVITATION_REVOKED &&
                        event.operatorPrincipalId == operatorId &&
                        event.targetId == invitationId.toString()
                },
            )
        }
    }

    @Test
    fun `revoke captures operator roles in audit event`() = runTest {
        coEvery { invitationRepository.findById(WaitlistInvitationId(invitationId)) } returns invitation()
        coEvery { invitationRepository.update(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify {
            auditPublisher.publish(
                match { event: AdminAuditEvent ->
                    event.operatorPlatformRoles == ownerRoles
                },
            )
        }
        assertNotNull(clock.instant())
        assertEquals(WaitlistInvitationStatus.ACTIVE, invitation().status)
    }

    private fun command(roles: Set<PlatformRole> = ownerRoles) = RevokeWaitlistInvitationCommand(
        operatorPrincipalId = operatorId,
        operatorRoles = roles,
        invitationId = invitationId,
    )

    private fun invitation(status: WaitlistInvitationStatus = WaitlistInvitationStatus.ACTIVE) = WaitlistInvitation(
        id = WaitlistInvitationId(invitationId),
        waitlistEntryId = entryId,
        tokenHash = "hashed-token",
        status = status,
        issuedAt = clock.instant().minusSeconds(3600),
        expiresAt = clock.instant().plusSeconds(604_800),
        createdBy = operatorId,
        deliveryStatus = InvitationDeliveryStatus.PENDING,
        version = 0,
    )
}
