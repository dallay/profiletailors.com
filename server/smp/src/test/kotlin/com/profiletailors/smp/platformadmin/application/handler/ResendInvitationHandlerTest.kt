package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.platformadmin.application.command.ResendInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationEventPublisher
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
import com.profiletailors.smp.platformadmin.domain.WorkspaceNotFoundException
import com.profiletailors.smp.tenancy.application.WorkspaceNameReader
import io.mockk.coEvery
import io.mockk.coVerify
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

class ResendInvitationHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val ttl = Duration.ofDays(7)
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val invitationId = InvitationId(UUID.fromString("11111111-1111-1111-1111-111111111111"))

    private val invitationRepository = mockk<InvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val eventPublisher = mockk<InvitationEventPublisher>(relaxed = true)
    private val transactionRunner = RecordingTransactionRunner()
    private val workspaceNameReader = mockk<WorkspaceNameReader>()

    private val tokenHasher = object : TokenHasher {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
    }
    private val invitationTokenCandidateKey = InvitationTokenCandidateKey { rawToken -> "candidate-$rawToken" }

    private val acceptUrlTemplate = AcceptUrlTemplate { rawToken ->
        "https://app.profiletailors.com/accept?token=$rawToken"
    }

    private val handler = ResendInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        eventPublisher = eventPublisher,
        transactionRunner = transactionRunner,
        workspaceNameReader = workspaceNameReader,
        clock = fixedClock,
        invitationTtl = ttl,
        tokenHasher = tokenHasher,
        invitationTokenCandidateKey = invitationTokenCandidateKey,
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
        coEvery { invitationRepository.updateIfVersionMatches(any(), any()) } returns true
        coEvery { workspaceNameReader.findName("ws-001") } returns "Existing Workspace"

        val result = handler.handle(command)

        assertThat(result.invitationId).isEqualTo(invitationId.value)
        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.expiresAt).isEqualTo(fixedClock.instant().plus(ttl).toString())
        assertThat(result.version).isEqualTo(1L)

        val eventSlot = slot<DirectInvitationResent>()
        coVerify { eventPublisher.publish(capture(eventSlot)) }
        assertThat(eventSlot.captured.recipient).isEqualTo("user@example.com")
        assertThat(eventSlot.captured.rawToken).isNotEmpty()
        assertThat(eventSlot.captured.previousInvitationId).isEqualTo(invitationId.value)
        assertThat(eventSlot.captured.workspaceName).isEqualTo("Existing Workspace")
        assertThat(eventSlot.captured.target).isEqualTo(InvitationTarget.EXISTING_WORKSPACE)
        assertThat(eventSlot.captured.deliveryId).isNotNull()
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
        coEvery { workspaceNameReader.findName("ws-001") } returns "Existing Workspace"

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

    @Test
    fun `handle throws when invitation is not a direct invitation`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation().copy(
            source = InvitationSource.WAITLIST,
            sourceReferenceId = "waitlist-entry-1",
        )

        assertThrows<InvitationNotResendableException> {
            handler.handle(command)
        }
        coVerify(exactly = 0) { invitationRepository.updateIfVersionMatches(any(), any()) }
    }

    @Test
    fun `handle throws when active invitation is past its expiration`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation().copy(
            createdAt = fixedClock.instant().minusSeconds(7_200),
            expiresAt = fixedClock.instant().minusSeconds(60),
        )
        coEvery { workspaceNameReader.findName("ws-001") } returns "Existing Workspace"

        assertThrows<InvitationNotResendableException> {
            handler.handle(command)
        }
        coVerify(exactly = 0) { invitationRepository.updateIfVersionMatches(any(), any()) }
    }

    @Test
    fun `handle throws WorkspaceNotFoundException when target workspace was removed`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()
        coEvery { workspaceNameReader.findName("ws-001") } returns null

        val thrown = assertThrows<WorkspaceNotFoundException> {
            handler.handle(command)
        }
        assertEquals("Workspace not found: ws-001", thrown.message)
        coVerify(exactly = 0) { invitationRepository.updateIfVersionMatches(any(), any()) }
        coVerify(exactly = 0) { eventPublisher.publish(any()) }
        coVerify(exactly = 0) { auditPublisher.publish(any()) }
    }

    @Test
    fun `two resends of the same invitation publish DirectInvitationResent with distinct delivery ids`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()
        coEvery { invitationRepository.updateIfVersionMatches(any(), any()) } returns true
        coEvery { workspaceNameReader.findName("ws-001") } returns "Existing Workspace"

        handler.handle(command)
        handler.handle(command)

        val events = mutableListOf<DirectInvitationResent>()
        coVerify(exactly = 2) { eventPublisher.publish(capture(events)) }
        assertNotNull(events.first().deliveryId)
        assertNotNull(events.last().deliveryId)
        assertThat(events.first().deliveryId).isNotEqualTo(events.last().deliveryId)
        assertThat(events.first().workspaceName).isEqualTo("Existing Workspace")
    }

    @Test
    fun `handle runs resend inside one atomic transaction`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()
        coEvery { invitationRepository.updateIfVersionMatches(any(), any()) } returns true
        coEvery { workspaceNameReader.findName("ws-001") } returns "Existing Workspace"

        handler.handle(command)

        assertEquals(1, transactionRunner.invocationCount)
        coVerify { invitationRepository.updateIfVersionMatches(any(), any()) }
        coVerify { auditPublisher.publish(any()) }
        coVerify { eventPublisher.publish(any()) }
    }

    @Test
    fun `handle propagates publisher failure for rollback`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation()
        coEvery { invitationRepository.updateIfVersionMatches(any(), any()) } returns true
        coEvery { workspaceNameReader.findName("ws-001") } returns "Existing Workspace"
        coEvery { eventPublisher.publish(any()) } throws RuntimeException("bus unavailable")

        assertThrows<RuntimeException> {
            handler.handle(command)
        }
        assertEquals(1, transactionRunner.invocationCount)
        assertEquals(1, transactionRunner.rollbackCount)
    }

    @Test
    fun `resend for NEW_WORKSPACE uses canonical copy without workspace lookup`() = runTest {
        val command = ResendInvitationCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = setOf(PlatformRole.PLATFORM_OWNER),
            invitationId = invitationId.value,
        )

        coEvery { invitationRepository.findById(invitationId) } returns activeInvitation().copy(
            target = InvitationTarget.NEW_WORKSPACE,
            workspaceId = null,
        )
        coEvery { invitationRepository.updateIfVersionMatches(any(), any()) } returns true

        val eventSlot = slot<DirectInvitationResent>()
        coEvery { eventPublisher.publish(capture(eventSlot)) } returns Unit

        handler.handle(command)

        coVerify(exactly = 0) { workspaceNameReader.findName(any()) }
        assertThat(eventSlot.captured.target).isEqualTo(InvitationTarget.NEW_WORKSPACE)
        assertThat(eventSlot.captured.workspaceName)
            .isEqualTo("You've been invited to create a new Profile Tailors workspace.")
        assertThat(eventSlot.captured.deliveryId).isNotNull()
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
