package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.platformadmin.domain.InvitationAccepted

private data class InvitationAcceptanceTransactionResult(
    val result: InvitationAcceptanceResult,
    val event: InvitationAccepted,
)

data class AcceptInvitationCommand(
    val rawToken: String,
    val authenticatedPrincipalId: String,
    val authenticatedEmail: String,
) : CommandWithResult<InvitationAcceptanceResult>

data class InvitationAcceptanceResult(val workspaceId: String, val membershipStatus: String)

class AcceptInvitationHandler(
    private val coordinator: InvitationActivationCoordinator,
    private val transactionRunner: AtomicTransactionRunner,
    private val eventPublisher: EventPublisher<DomainEvent> = EventPublisher.noop(),
) : CommandWithResultHandler<AcceptInvitationCommand, InvitationAcceptanceResult> {
    override suspend fun handle(command: AcceptInvitationCommand): InvitationAcceptanceResult {
        val transactionResult = transactionRunner.runAtomically {
            val result = coordinator.activateForRegistration(
                rawToken = command.rawToken,
                email = command.authenticatedEmail,
                principalId = command.authenticatedPrincipalId,
            )
            val workspaceId = result.invitation.workspaceId
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Invitation workspace could not be resolved.")
            InvitationAcceptanceTransactionResult(
                result = InvitationAcceptanceResult(
                    workspaceId = workspaceId,
                    membershipStatus = result.membershipStatus.name,
                ),
                event = InvitationAccepted(
                    invitationId = result.invitation.id.value,
                    principalId = requireNotNull(result.invitation.acceptedPrincipalId),
                    workspaceId = workspaceId,
                    target = result.invitation.target,
                    occurredAt = requireNotNull(result.invitation.acceptedAt),
                ),
            )
        }
        eventPublisher.publish(transactionResult.event)
        return transactionResult.result
    }
}
