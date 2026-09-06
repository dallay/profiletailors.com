package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class AcceptInvitationHandlerTest {
    private val now = Instant.parse("2026-08-09T10:00:00Z")

    @Test
    fun `rejects when coordinator throws`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        coEvery {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "invitee@example.com",
                principalId = "principal-1",
            )
        } throws InvitationNotAcceptableException("unavailable")

        assertThrows<InvitationNotAcceptableException> {
            handler(coordinator).handle(
                AcceptInvitationCommand(
                    rawToken = "raw-token",
                    authenticatedPrincipalId = "principal-1",
                    authenticatedEmail = "invitee@example.com",
                ),
            )
        }
    }

    @Test
    fun `accepts and returns workspaceId and membershipStatus`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
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

        val result = handler(coordinator).handle(
            AcceptInvitationCommand(
                rawToken = "raw-token",
                authenticatedPrincipalId = "principal-1",
                authenticatedEmail = "invitee@example.com",
            ),
        )

        assertEquals("workspace-a", result.workspaceId)
        assertEquals(WorkspaceMembershipStatus.ACTIVE.name, result.membershipStatus)
        coVerify {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "invitee@example.com",
                principalId = "principal-1",
            )
        }
    }

    @Test
    fun `falls back to invitation id as workspaceId when workspaceId is null`() = runTest {
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

        val result = handler(coordinator).handle(
            AcceptInvitationCommand(
                rawToken = "raw-token",
                authenticatedPrincipalId = "principal-1",
                authenticatedEmail = "invitee@example.com",
            ),
        )

        assertEquals(invitationId.toString(), result.workspaceId)
        assertEquals(WorkspaceMembershipStatus.ACTIVE.name, result.membershipStatus)
    }

    private fun handler(coordinator: InvitationActivationCoordinator) = AcceptInvitationHandler(
        coordinator = coordinator,
    )
}
