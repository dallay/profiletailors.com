package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner

data class AcceptInvitationCommand(
    val rawToken: String,
    val authenticatedPrincipalId: String,
    val authenticatedEmail: String,
) : CommandWithResult<InvitationAcceptanceResult>

data class InvitationAcceptanceResult(val workspaceId: String, val membershipStatus: String)

class AcceptInvitationHandler(
    private val coordinator: InvitationActivationCoordinator,
    private val transactionRunner: AtomicTransactionRunner,
) : CommandWithResultHandler<AcceptInvitationCommand, InvitationAcceptanceResult> {
    override suspend fun handle(command: AcceptInvitationCommand): InvitationAcceptanceResult =
        transactionRunner.runAtomically {
            val result = coordinator.activateForRegistration(
                rawToken = command.rawToken,
                email = command.authenticatedEmail,
                principalId = command.authenticatedPrincipalId,
            )
            InvitationAcceptanceResult(
                workspaceId = result.invitation.workspaceId
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Invitation workspace could not be resolved."),
                membershipStatus = result.membershipStatus.name,
            )
        }
}
