package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.platformadmin.application.command.CreateInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CreateInvitationHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val ttl = Duration.ofDays(7)
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = InvitationId.generate()

    private val invitationRepository = mockk<InvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val eventPublisher = mockk<EventPublisher<DomainEvent>>(relaxed = true)
    private val telemetry = mockk<InvitationTelemetry>(relaxed = true)

    private val tokenHasher = object : TokenHasher, InvitationTokenCandidateKey {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
        override fun candidateKey(rawToken: String): String = "candidate-$rawToken"
    }

    private val handler = CreateInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        eventPublisher = eventPublisher,
        clock = fixedClock,
        invitationTtl = ttl,
        tokenHasher = tokenHasher,
        telemetry = telemetry,
    )

    @Test
    fun `handle happy path creates invitation and publishes event`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        coEvery { invitationRepository.hasActiveInvitationFor(any(), any(), any()) } returns false
        coEvery { invitationRepository.save(any(), any()) } answers {
            val invitation = firstArg<Invitation>()
            invitation.copy(id = invitationId)
        }

        val result = handler.handle(command)

        assertEquals(invitationId.value, result.invitationId)
        assertEquals("ACTIVE", result.status)
        assertEquals(fixedClock.instant().plus(ttl).toString(), result.expiresAt)
        assertEquals(0L, result.version)

        val eventSlot = slot<InvitationIssued>()
        coVerify { eventPublisher.publish(capture(eventSlot)) }
        assertThat(eventSlot.captured.recipientEmail).isEqualTo("user@example.com")
        assertThat(eventSlot.captured.rawToken).isNotEmpty()
        verify { telemetry.recordInvitationCreated() }
    }

    @Test
    fun `should persist issuedBy as prefixed platform principal id when creating a direct invitation`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        coEvery { invitationRepository.hasActiveInvitationFor(any(), any(), any()) } returns false
        val savedSlot = slot<Invitation>()
        coEvery { invitationRepository.save(capture(savedSlot), any()) } answers {
            firstArg<Invitation>()
        }

        handler.handle(command)

        savedSlot.captured.issuedBy shouldBe "user-$operatorId"
    }

    @Test
    fun `handle throws when active invitation already exists`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        coEvery {
            invitationRepository.hasActiveInvitationFor("user@example.com", "workspace-001", any())
        } returns true

        assertThrows<com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle rejects existing-workspace invitation without workspace`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = null,
        )

        assertThrows<IllegalArgumentException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle creates new-workspace invitation without workspace`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.NEW_WORKSPACE,
            workspaceId = null,
        )

        coEvery { invitationRepository.save(any(), any()) } answers {
            val invitation = firstArg<Invitation>()
            invitation.copy(id = invitationId)
        }

        val result = handler.handle(command)

        assertEquals(invitationId.value, result.invitationId)
        assertEquals("ACTIVE", result.status)
        coVerify(exactly = 0) { invitationRepository.hasActiveInvitationFor(any(), any(), any()) }
    }

    @Test
    fun `handle normalizes email with surrounding whitespace before lookup and persistence`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "  User@Example.com  ",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        coEvery {
            invitationRepository.hasActiveInvitationFor("user@example.com", "workspace-001", any())
        } returns false
        coEvery { invitationRepository.save(any(), any()) } answers {
            val invitation = firstArg<Invitation>()
            invitation.copy(id = invitationId)
        }

        val result = handler.handle(command)

        assertEquals(invitationId.value, result.invitationId)
        coVerify { invitationRepository.hasActiveInvitationFor("user@example.com", "workspace-001", any()) }
        val savedSlot = slot<Invitation>()
        coVerify { invitationRepository.save(capture(savedSlot), any()) }
        assertThat(savedSlot.captured.invitedEmailNormalized).isEqualTo("user@example.com")
        val eventSlot = slot<InvitationIssued>()
        coVerify { eventPublisher.publish(capture(eventSlot)) }
        assertThat(eventSlot.captured.recipientEmail).isEqualTo("user@example.com")
    }

    @Test
    fun `handle throws when operator lacks INVITATIONS_CREATE permission`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.SUPPORT_AGENT),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command)
        }
    }

    @Test
    fun `handle keeps the persisted invitation when event publish fails`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        coEvery { invitationRepository.hasActiveInvitationFor(any(), any(), any()) } returns false
        coEvery { invitationRepository.save(any(), any()) } answers {
            firstArg<Invitation>()
        }
        coEvery { eventPublisher.publish(any<DomainEvent>()) } throws RuntimeException("bus unavailable")

        assertThrows<RuntimeException> {
            handler.handle(command)
        }
        coVerify { invitationRepository.save(any(), any()) }
    }
}
