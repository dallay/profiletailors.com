package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.platformadmin.application.command.CreateInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationEventPublisher
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
import com.profiletailors.smp.platformadmin.domain.WorkspaceNotFoundException
import com.profiletailors.smp.tenancy.application.WorkspaceNameReader
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
    private val eventPublisher = mockk<InvitationEventPublisher>(relaxed = true)
    private val transactionRunner = RecordingTransactionRunner()
    private val workspaceNameReader = mockk<WorkspaceNameReader>()
    private val telemetry = mockk<InvitationTelemetry>(relaxed = true)

    private val tokenHasher = object : TokenHasher {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
    }
    private val invitationTokenCandidateKey = InvitationTokenCandidateKey { rawToken -> "candidate-$rawToken" }

    private val handler = CreateInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        eventPublisher = eventPublisher,
        transactionRunner = transactionRunner,
        workspaceNameReader = workspaceNameReader,
        clock = fixedClock,
        invitationTtl = ttl,
        tokenHasher = tokenHasher,
        invitationTokenCandidateKey = invitationTokenCandidateKey,
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
        coEvery { workspaceNameReader.findName("workspace-001") } returns "Existing Workspace"
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
        assertThat(eventSlot.captured.workspaceName).isEqualTo("Existing Workspace")
        assertThat(eventSlot.captured.target).isEqualTo(InvitationTarget.EXISTING_WORKSPACE)
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
        coEvery { workspaceNameReader.findName("workspace-001") } returns "Existing Workspace"
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

        coEvery { workspaceNameReader.findName("workspace-001") } returns "Existing Workspace"
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
        coEvery { workspaceNameReader.findName("workspace-001") } returns "Existing Workspace"
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
        assertThat(eventSlot.captured.workspaceName).isEqualTo("Existing Workspace")
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
    fun `handle runs creation inside one atomic transaction`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        coEvery { invitationRepository.hasActiveInvitationFor(any(), any(), any()) } returns false
        coEvery { workspaceNameReader.findName("workspace-001") } returns "Existing Workspace"
        coEvery { invitationRepository.save(any(), any()) } answers {
            val invitation = firstArg<Invitation>()
            invitation.copy(id = invitationId)
        }

        handler.handle(command)

        assertEquals(1, transactionRunner.invocationCount)
        coVerify { invitationRepository.save(any(), any()) }
        coVerify { auditPublisher.publish(any()) }
        coVerify { eventPublisher.publish(any()) }
    }

    @Test
    fun `handle propagates publisher failure for rollback without telemetry`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-001",
        )

        coEvery { invitationRepository.hasActiveInvitationFor(any(), any(), any()) } returns false
        coEvery { workspaceNameReader.findName("workspace-001") } returns "Existing Workspace"
        coEvery { invitationRepository.save(any(), any()) } answers {
            firstArg<Invitation>()
        }
        coEvery { eventPublisher.publish(any()) } throws RuntimeException("bus unavailable")

        assertThrows<RuntimeException> {
            handler.handle(command)
        }
        assertEquals(1, transactionRunner.invocationCount)
        assertEquals(1, transactionRunner.rollbackCount)
        coVerify(exactly = 0) { telemetry.recordInvitationCreated() }
    }

    @Test
    fun `handle resolves workspace name and throws WorkspaceNotFoundException when lookup returns null`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "user@example.com",
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "missing-workspace",
        )

        coEvery { workspaceNameReader.findName("missing-workspace") } returns null

        val thrown = assertThrows<WorkspaceNotFoundException> {
            handler.handle(command)
        }
        assertEquals("Workspace not found: missing-workspace", thrown.message)
        coVerify(exactly = 0) { invitationRepository.hasActiveInvitationFor(any(), any(), any()) }
        coVerify(exactly = 0) { invitationRepository.save(any(), any()) }
        coVerify(exactly = 0) { eventPublisher.publish(any()) }
        coVerify(exactly = 0) { auditPublisher.publish(any()) }
    }

    @Test
    fun `handle uses the canonical NEW_WORKSPACE copy and target for new-workspace invitations`() = runTest {
        val command = CreateInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            email = "new-workspace@example.com",
            target = InvitationTarget.NEW_WORKSPACE,
            workspaceId = null,
        )

        coEvery { invitationRepository.save(any(), any()) } answers {
            val invitation = firstArg<Invitation>()
            invitation.copy(id = invitationId)
        }

        val result = handler.handle(command)

        assertEquals(invitationId.value, result.invitationId)
        coVerify(exactly = 0) { workspaceNameReader.findName(any()) }
        coVerify(exactly = 0) { invitationRepository.hasActiveInvitationFor(any(), any(), any()) }
        val eventSlot = slot<InvitationIssued>()
        coVerify { eventPublisher.publish(capture(eventSlot)) }
        assertThat(eventSlot.captured.target).isEqualTo(InvitationTarget.NEW_WORKSPACE)
        assertThat(eventSlot.captured.workspaceName)
            .isEqualTo("You've been invited to create a new Profile Tailors workspace.")
        assertThat(eventSlot.captured.deliveryId).isNull()
    }

    private class RecordingTransactionRunner : AtomicTransactionRunner {
        var invocationCount = 0
        var rollbackCount = 0

        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            invocationCount += 1
            return try {
                block()
            } catch (error: Throwable) {
                rollbackCount += 1
                throw error
            }
        }
    }
}
