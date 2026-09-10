package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.identity.application.InvitationRegistrationContext
import com.profiletailors.smp.identity.application.InvitationRegistrationSource
import com.profiletailors.smp.identity.application.InvitationRegistrationTarget
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
        val context = context(invitation)
        coEvery {
            coordinator.complete(
                context = context,
                rawToken = "raw-token",
                principalId = "principal-1",
                displayName = "invitee",
            )
        } returns InvitationActivationCoordinator.InvitationActivationResult(
            invitation = invitation.accept(now, "principal-1"),
            membershipStatus = WorkspaceMembershipStatus.ACTIVE,
        )

        val result = adapter(coordinator).complete(
            context = context,
            rawToken = "raw-token",
            principalId = "principal-1",
            displayName = "invitee",
        )

        result.workspaceId shouldBe "workspace-a"
        coVerify {
            coordinator.complete(
                context = context,
                rawToken = "raw-token",
                principalId = "principal-1",
                displayName = "invitee",
            )
        }
    }

    @Test
    fun `should reject invitation when coordinator throws`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val context = InvitationRegistrationContext(
            invitationId = "invitation-1",
            target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
            workspaceId = "workspace-a",
            source = InvitationRegistrationSource.DIRECT,
        )
        coEvery {
            coordinator.complete(
                context = context,
                rawToken = "raw-token",
                principalId = "principal-1",
                displayName = "invitee",
            )
        } throws InvitationNotAcceptableException("unavailable")

        shouldThrow<InvitationNotAcceptableException> {
            adapter(coordinator).complete(
                context = context,
                rawToken = "raw-token",
                principalId = "principal-1",
                displayName = "invitee",
            )
        }
    }

    @Test
    fun `rejects a new-workspace invitation without a resolved workspace instead of using invitation id`() = runTest {
        val coordinator = mockk<InvitationActivationCoordinator>()
        val invitation = newWorkspaceInvitation()
        val context = context(invitation)
        coEvery {
            coordinator.complete(
                context = context,
                rawToken = "raw-token",
                principalId = "principal-1",
                displayName = "invitee",
            )
        } returns InvitationActivationCoordinator.InvitationActivationResult(
            invitation = invitation,
            membershipStatus = WorkspaceMembershipStatus.ACTIVE,
        )

        val exception = shouldThrow<IllegalStateException> {
            adapter(coordinator).complete(
                context = context,
                rawToken = "raw-token",
                principalId = "principal-1",
                displayName = "invitee",
            )
        }

        exception.message.orEmpty().contains(invitation.id.value.toString()) shouldBe false
    }

    private fun context(invitation: Invitation) = InvitationRegistrationContext(
        invitationId = invitation.id.value.toString(),
        target = InvitationRegistrationTarget.valueOf(invitation.target.name),
        workspaceId = invitation.workspaceId,
        source = InvitationRegistrationSource.valueOf(invitation.source.name),
    )

    private fun adapter(coordinator: InvitationActivationCoordinator) = InvitationRegistrationGatewayAdapter(
        coordinator = coordinator,
    )
}
