package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler

data class AcceptInvitationCommand(
    val rawToken: String,
    val authenticatedPrincipalId: String,
    val authenticatedEmail: String,
) : CommandWithResult<InvitationAcceptanceResult>

data class InvitationAcceptanceResult(val workspaceId: String, val membershipStatus: String)

class AcceptInvitationHandler(private val coordinator: InvitationActivationCoordinator) :
    CommandWithResultHandler<AcceptInvitationCommand, InvitationAcceptanceResult> {
    override suspend fun handle(command: AcceptInvitationCommand): InvitationAcceptanceResult {
        val result = coordinator.activateForRegistration(
            rawToken = command.rawToken,
            email = command.authenticatedEmail,
            principalId = command.authenticatedPrincipalId,
        )
        return InvitationAcceptanceResult(
            workspaceId = result.invitation.workspaceId
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Invitation workspace could not be resolved."),
            membershipStatus = result.membershipStatus.name,
        )
    }
}
