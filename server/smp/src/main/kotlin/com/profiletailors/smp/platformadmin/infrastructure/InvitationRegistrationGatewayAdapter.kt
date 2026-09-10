package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.identity.application.InvitationRegistrationContext
import com.profiletailors.smp.identity.application.InvitationRegistrationGateway
import com.profiletailors.smp.identity.application.InvitationRegistrationResult
import com.profiletailors.smp.platformadmin.application.InvitationActivationCoordinator
import com.profiletailors.smp.platformadmin.domain.InvitationAccepted
import org.springframework.stereotype.Component

@Component
class InvitationRegistrationGatewayAdapter(private val coordinator: InvitationActivationCoordinator) :
    InvitationRegistrationGateway {
    override suspend fun prepare(rawToken: String, normalizedEmail: String): InvitationRegistrationContext =
        coordinator.prepare(rawToken, normalizedEmail)

    override suspend fun complete(
        context: InvitationRegistrationContext,
        rawToken: String,
        principalId: String,
        displayName: String,
    ): InvitationRegistrationResult {
        val result = coordinator.complete(
            context = context,
            rawToken = rawToken,
            principalId = principalId,
            displayName = displayName,
        )
        val workspaceId = result.invitation.workspaceId
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Invitation workspace could not be resolved.")
        return InvitationRegistrationResult(
            workspaceId = workspaceId,
            membershipStatus = result.membershipStatus.name,
            postCommitEvent = InvitationAccepted(
                invitationId = result.invitation.id.value,
                principalId = requireNotNull(result.invitation.acceptedPrincipalId),
                workspaceId = workspaceId,
                target = result.invitation.target,
                occurredAt = requireNotNull(result.invitation.acceptedAt),
            ),
        )
    }
}
