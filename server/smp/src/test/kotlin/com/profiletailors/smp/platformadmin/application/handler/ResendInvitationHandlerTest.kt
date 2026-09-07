package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.platformadmin.application.command.ResendInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.domain.DirectInvitationResent
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotResendableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ResendInvitationHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val ttl = Duration.ofDays(7)
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = InvitationId(UUID.fromString("11111111-1111-1111-1111-111111111111"))

    private val invitationRepository = mockk<InvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val eventPublisher = mockk<EventPublisher<DomainEvent>>(relaxed = true)

    private val tokenHasher = object : TokenHasher, InvitationTokenCandidateKey {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
        override fun candidateKey(rawToken: String): String = "candidate-$rawToken"
    }

    private val acceptUrlTemplate = AcceptUrlTemplate { rawToken ->
        "https://app.profiletailors.com/accept?token=$rawToken"
    }

    private val handler = ResendInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        eventPublisher = eventPublisher,
        clock = fixedClock,
        invitationTtl = ttl,
        tokenHasher = tokenHasher,
        acceptUrlTemplateFn = acceptUrlTemplate,
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
    fun `handle happy path resends invitation without exposing the token`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns true

        val result = handler.handle(command)

        assertThat(result.invitationId).isEqualTo(invitationId.value)
        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.expiresAt).isEqualTo(fixedClock.instant().plus(ttl).toString())

        val eventSlot = slot<DirectInvitationResent>()
        coVerify { eventPublisher.publish(capture(eventSlot)) }
        assertThat(eventSlot.captured.recipient).isEqualTo("user@example.com")
        assertThat(eventSlot.captured.rawToken).isNotEmpty()
        assertThat(eventSlot.captured.previousInvitationId).isEqualTo(invitationId.value)
    }

    @Test
    fun `handle throws when invitation not found`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns null

        assertThrows<InvitationNotFoundException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle throws when invitation not active`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation().copy(
            status = InvitationStatus.EXPIRED,
        )

        assertThrows<InvitationNotResendableException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle throws when operator lacks INVITATIONS_RESEND permission`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.SUPPORT_AGENT),
            invitationId = invitationId.value,
        )

        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command)
        }
    }
}
