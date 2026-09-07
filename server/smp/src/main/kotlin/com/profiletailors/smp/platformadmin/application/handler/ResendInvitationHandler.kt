package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.platformadmin.application.command.ResendInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.result.ResendInvitationResult
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.DirectInvitationResent
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotResendableException
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.time.Duration
import java.util.UUID

class ResendInvitationHandler(
    private val invitationRepository: InvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val clock: Clock,
    private val invitationTtl: Duration,
    private val tokenHasher: TokenHasher,
    private val acceptUrlTemplateFn: AcceptUrlTemplate,
) {
    suspend fun handle(command: ResendInvitationCommand): ResendInvitationResult {
        requireResendPermission(command.operatorRoles)

        val invitationId = InvitationId(command.invitationId)
        val invitation = invitationRepository.findById(invitationId)
            ?: throw InvitationNotFoundException(command.invitationId.toString())

        val now = clock.instant()
        val rawToken = com.profiletailors.smp.platformadmin.domain.InvitationTokenGenerator.generate()
        val tokenHash: String = tokenHasher.hash(rawToken)
        val candidateKey: String = tokenHasher.requireCandidateKey(rawToken)
        val newExpiresAt = now + invitationTtl

        val resentInvitation = invitation.resend(tokenHash, newExpiresAt)
        val updated = invitationRepository.updateIfVersionMatches(resentInvitation)
        if (!updated) {
            throw InvitationNotResendableException(invitationId.value.toString())
        }

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.INVITATION_RESENT,
                targetType = "Invitation",
                targetId = invitationId.value.toString(),
                result = AdminAuditResult.SUCCEEDED,
            ),
        )

        val acceptUrl = acceptUrlTemplateFn.build(rawToken)
        eventPublisher.publish(
            DirectInvitationResent(
                invitationId = invitationId.value,
                operatorPrincipalId = command.operatorPrincipalId,
                recipient = invitation.invitedEmailNormalized,
                workspaceName = invitation.workspaceId ?: "",
                acceptUrl = acceptUrl,
                locale = null,
                rawToken = rawToken,
                previousInvitationId = invitationId.value,
            ),
        )

        return ResendInvitationResult(
            invitationId = invitationId.value,
            status = InvitationStatus.ACTIVE.name,
            expiresAt = newExpiresAt.toString(),
        )
    }

    private fun requireResendPermission(operatorRoles: Set<PlatformRole>) {
        if (PlatformPermission.INVITATIONS_RESEND !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_RESEND)
        }
    }

    private fun TokenHasher.requireCandidateKey(rawToken: String): String =
        (this as? InvitationTokenCandidateKey)?.candidateKey(rawToken)
            ?: throw IllegalStateException("TokenHasher must implement InvitationTokenCandidateKey")
}
