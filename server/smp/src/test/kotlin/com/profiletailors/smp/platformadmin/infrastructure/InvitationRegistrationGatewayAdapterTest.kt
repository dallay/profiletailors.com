package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.platformadmin.application.InvitationActivationCoordinator
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class InvitationRegistrationGatewayAdapterTest {
    private val now = Instant.parse("2026-08-09T10:00:00Z")

    private fun existingWorkspaceInvitation(
        status: InvitationStatus = InvitationStatus.ACTIVE,
        workspaceId: String? = "workspace-a",
    ) = Invitation(
        id = InvitationId.generate(),
        source = InvitationSource.WAITLIST,
        sourceReferenceId = "waitlist-1",
        target = InvitationTarget.EXISTING_WORKSPACE,
        workspaceId = workspaceId,
        invitedEmailNormalized = "invitee@example.com",
        tokenHash = "hashed-token",
        status = status,
        issuedBy = "issuer-1",
        createdAt = now.minusSeconds(60),
        expiresAt = now.plusSeconds(3600),
    )

    private fun newWorkspaceInvitation(
        status: InvitationStatus = InvitationStatus.ACTIVE,
        workspaceId: String? = null,
    ) = Invitation(
        id = InvitationId.generate(),
        source = InvitationSource.WAITLIST,
        sourceReferenceId = "waitlist-1",
        target = InvitationTarget.NEW_WORKSPACE,
        workspaceId = workspaceId,
        invitedEmailNormalized = "invitee@example.com",
        tokenHash = "hashed-token",
        status = status,
        issuedBy = "issuer-1",
        createdAt = now.minusSeconds(60),
        expiresAt = now.plusSeconds(3600),
    )

    @Test
    fun `should return the workspace when invitation matches`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val invitation = existingWorkspaceInvitation()
        coEvery {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = " Invitee@Example.com ",
                principalId = "principal-1",
            )
        } returns InvitationActivationCoordinator.InvitationActivationResult(
            invitation = invitation,
            membershipStatus = WorkspaceMembershipStatus.ACTIVE,
        )

        val workspaceId = adapter(coordinator).acceptForRegistration(
            rawToken = "raw-token",
            email = " Invitee@Example.com ",
            principalId = "principal-1",
        )

        workspaceId shouldBe "workspace-a"
        coVerify {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = " Invitee@Example.com ",
                principalId = "principal-1",
            )
        }
    }

    @Test
    fun `should reject invitation when coordinator throws`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        coEvery {
            coordinator.activateForRegistration(
                rawToken = "raw-token",
                email = "other@example.com",
                principalId = "principal-1",
            )
        } throws InvitationNotAcceptableException("unavailable")

        shouldThrow<InvitationNotAcceptableException> {
            adapter(coordinator).acceptForRegistration(
                rawToken = "raw-token",
                email = "other@example.com",
                principalId = "principal-1",
            )
        }
    }

    @Test
    fun `should return invitation id when workspaceId is null`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val invitation = newWorkspaceInvitation(status = InvitationStatus.ACTIVE, workspaceId = null)
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

        val result = adapter(coordinator).acceptForRegistration(
            rawToken = "raw-token",
            email = "invitee@example.com",
            principalId = "principal-1",
        )

        result shouldBe invitation.id.value.toString()
    }

    private fun adapter(coordinator: InvitationActivationCoordinator) = InvitationRegistrationGatewayAdapter(
        coordinator = coordinator,
    )
}
