package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationAccepted
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class AcceptInvitationHandlerTest {
    private val now = Instant.parse("2026-08-09T10:00:00Z")

    @Test
    fun `rejects when coordinator throws`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val transactionRunner = RecordingTransactionRunner()
        coEvery {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "invitee@example.com",
                principalId = "principal-1",
            )
        } throws InvitationNotAcceptableException("unavailable")

        assertThrows<InvitationNotAcceptableException> {
            handler(coordinator, transactionRunner).handle(
                AcceptInvitationCommand(
                    rawToken = "raw-token",
                    authenticatedPrincipalId = "principal-1",
                    authenticatedEmail = "invitee@example.com",
                ),
            )
        }

        assertEquals(1, transactionRunner.invocationCount)
        assertEquals(1, transactionRunner.rollbackCount)
    }

    @Test
    fun `runs authenticated acceptance inside one transaction`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val invitation = invitation(workspaceId = "workspace-a").accept(now, "principal-1")
        coEvery {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "invitee@example.com",
                principalId = "principal-1",
            )
        } returns InvitationActivationCoordinator.InvitationActivationResult(
            invitation = invitation,
            membershipStatus = WorkspaceMembershipStatus.ACTIVE,
        )
        val transactionRunner = RecordingTransactionRunner()

        handler(coordinator, transactionRunner).handle(command())

        assertEquals(1, transactionRunner.invocationCount)
    }

    @Test
    fun `should publish acceptance event when invitation is accepted`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val eventPublisher = mockk<EventPublisher<DomainEvent>>(relaxed = true)
        val invitation = Invitation(
            id = InvitationId(UUID.randomUUID()),
            source = InvitationSource.DIRECT,
            sourceReferenceId = null,
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-a",
            invitedEmailNormalized = "invitee@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACTIVE,
            issuedBy = "issuer-1",
            createdAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
        ).accept(now, "principal-1")
        coEvery {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "invitee@example.com",
                principalId = "principal-1",
            )
        } returns InvitationActivationCoordinator.InvitationActivationResult(
            invitation = invitation,
            membershipStatus = WorkspaceMembershipStatus.ACTIVE,
        )

        val result = AcceptInvitationHandler(
            coordinator = coordinator,
            transactionRunner = NoOpTransactionRunner(),
            eventPublisher = eventPublisher,
        ).handle(
            AcceptInvitationCommand(
                rawToken = "raw-token",
                authenticatedPrincipalId = "principal-1",
                authenticatedEmail = "invitee@example.com",
            ),
        )

        result.workspaceId shouldBe "workspace-a"
        result.membershipStatus shouldBe WorkspaceMembershipStatus.ACTIVE.name
        coVerify {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "invitee@example.com",
                principalId = "principal-1",
            )
        }
        coVerify(exactly = 1) {
            eventPublisher.publish(
                match<InvitationAccepted> { event ->
                    event.invitationId == invitation.id.value &&
                        event.principalId == "principal-1" &&
                        event.workspaceId == "workspace-a" &&
                        event.target == InvitationTarget.EXISTING_WORKSPACE &&
                        event.occurredAt == now
                },
            )
        }
    }

    @Test
    fun `rejects an unresolved workspace instead of returning invitation id as workspaceId`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val invitationId = UUID.randomUUID()
        val invitation = Invitation(
            id = InvitationId(invitationId),
            source = InvitationSource.WAITLIST,
            sourceReferenceId = "waitlist-1",
            target = InvitationTarget.NEW_WORKSPACE,
            workspaceId = null,
            invitedEmailNormalized = "invitee@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACTIVE,
            issuedBy = "issuer-1",
            createdAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
        )
        coEvery {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "invitee@example.com",
                principalId = "principal-1",
            )
        } returns InvitationActivationCoordinator.InvitationActivationResult(
            invitation = invitation,
            membershipStatus = WorkspaceMembershipStatus.ACTIVE,
        )

        val exception = assertThrows<IllegalStateException> {
            handler(coordinator).handle(
                AcceptInvitationCommand(
                    rawToken = "raw-token",
                    authenticatedPrincipalId = "principal-1",
                    authenticatedEmail = "invitee@example.com",
                ),
            )
        }

        assertFalse(exception.message.orEmpty().contains(invitationId.toString()))
    }

    private fun handler(coordinator: InvitationActivationCoordinator) = AcceptInvitationHandler(
        coordinator = coordinator,
        transactionRunner = NoOpTransactionRunner(),
    )

    private fun handler(coordinator: InvitationActivationCoordinator, transactionRunner: AtomicTransactionRunner) =
        AcceptInvitationHandler(
            coordinator = coordinator,
            transactionRunner = transactionRunner,
        )

    private fun command() = AcceptInvitationCommand(
        rawToken = "raw-token",
        authenticatedPrincipalId = "principal-1",
        authenticatedEmail = "invitee@example.com",
    )

    private fun invitation(workspaceId: String?) = Invitation(
        id = InvitationId(UUID.randomUUID()),
        source = InvitationSource.DIRECT,
        sourceReferenceId = null,
        target = if (workspaceId == null) InvitationTarget.NEW_WORKSPACE else InvitationTarget.EXISTING_WORKSPACE,
        workspaceId = workspaceId,
        invitedEmailNormalized = "invitee@example.com",
        tokenHash = "hashed-token",
        status = InvitationStatus.ACTIVE,
        issuedBy = "issuer-1",
        createdAt = now.minusSeconds(60),
        expiresAt = now.plusSeconds(3600),
    )

    private class NoOpTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
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
