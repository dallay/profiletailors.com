package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.RevokeInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotRevocableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.InvitationVersionConflictException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RevokeInvitationHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = InvitationId(UUID.fromString("11111111-1111-1111-1111-111111111111"))

    private val invitationRepository = mockk<InvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val telemetry = mockk<InvitationTelemetry>(relaxed = true)

    private val handler = RevokeInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = fixedClock,
        telemetry = telemetry,
    )

    private fun activeInvitation() = Invitation(
        id = invitationId,
        source = InvitationSource.DIRECT,
        sourceReferenceId = null,
        target = InvitationTarget.EXISTING_WORKSPACE,
        workspaceId = "ws-001",
        invitedEmailNormalized = "user@example.com",
        tokenHash = "hash",
        status = InvitationStatus.ACTIVE,
        issuedBy = operatorId.toString(),
        createdAt = fixedClock.instant(),
        expiresAt = fixedClock.instant().plusSeconds(86_400),
    )

    @Test
    fun `handle happy path revokes and returns result`() = runTest {
        val command = RevokeInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
            expectedVersion = 0L,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns true

        val result = handler.handle(command)

        assertThat(result.invitationId).isEqualTo(invitationId.value)
        coVerify { invitationRepository.updateIfVersionMatches(match { it.status == InvitationStatus.REVOKED }) }
        verify { telemetry.recordInvitationRevoked() }
    }

    @Test
    fun `handle throws when invitation not found`() = runTest {
        val command = RevokeInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
            expectedVersion = 0L,
        )

        coEvery { invitationRepository.findById(invitationId) } returns null

        assertThrows<InvitationNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle throws when invitation not active`() = runTest {
        val command = RevokeInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
            expectedVersion = 0L,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation().copy(
            status = InvitationStatus.EXPIRED,
        )

        assertThrows<InvitationNotRevocableException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle throws when version conflict`() = runTest {
        val command = RevokeInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
            expectedVersion = 99L,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()

        assertThrows<InvitationVersionConflictException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle throws version conflict when persistence reports a lost update`() = runTest {
        val command = RevokeInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
            expectedVersion = 0L,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns false

        assertThrows<InvitationVersionConflictException> {
            handler.handle(command)
        }
        coVerify(exactly = 0) { auditPublisher.publish(any()) }
        verify(exactly = 0) { telemetry.recordInvitationRevoked() }
    }

    @Test
    fun `handle throws when operator lacks INVITATIONS_REVOKE permission`() = runTest {
        val command = RevokeInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.SUPPORT_AGENT),
            invitationId = invitationId.value,
            expectedVersion = 0L,
        )

        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command)
        }
    }
}
