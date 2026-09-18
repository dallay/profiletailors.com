package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.notifications.domain.InvitationEmail
import com.profiletailors.smp.platformadmin.application.command.ResendInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationEventPublisher
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
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.InvitationTokenGenerator
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.WorkspaceNotFoundException
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.tenancy.application.WorkspaceNameReader
import java.time.Clock
import java.time.Duration
import java.util.UUID

class ResendInvitationHandler(
    private val invitationRepository: InvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val eventPublisher: InvitationEventPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val workspaceNameReader: WorkspaceNameReader,
    private val clock: Clock,
    private val invitationTtl: Duration,
    private val tokenHasher: TokenHasher,
    private val invitationTokenCandidateKey: InvitationTokenCandidateKey,
    private val acceptUrlTemplateFn: AcceptUrlTemplate,
) {
    suspend fun handle(command: ResendInvitationCommand): ResendInvitationResult {
        requireResendPermission(command.operatorRoles)

        return transactionRunner.runAtomically {
            val invitationId = InvitationId(command.invitationId)
            val invitation = invitationRepository.findById(invitationId)
                ?: throw InvitationNotFoundException(command.invitationId.toString())
            if (invitation.source != InvitationSource.DIRECT) {
                throw InvitationNotResendableException(command.invitationId.toString())
            }

            val resolvedWorkspaceName = resolveWorkspaceName(invitation.target, invitation.workspaceId)

            val now = clock.instant()
            val rawToken = InvitationTokenGenerator.generate()
            val tokenHash: String = tokenHasher.hash(rawToken)
            val candidateKey = invitationTokenCandidateKey.candidateKey(rawToken)
            val newExpiresAt = now + invitationTtl

            val resentInvitation = invitation.resend(tokenHash, newExpiresAt, now)
            val updated = invitationRepository.updateIfVersionMatches(resentInvitation, candidateKey)
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
            val deliveryId = UUID.randomUUID()
            eventPublisher.publish(
                DirectInvitationResent(
                    invitationId = invitationId.value,
                    operatorPrincipalId = command.operatorPrincipalId,
                    recipient = invitation.invitedEmailNormalized,
                    workspaceName = resolvedWorkspaceName,
                    target = invitation.target,
                    acceptUrl = acceptUrl,
                    locale = null,
                    rawToken = rawToken,
                    deliveryId = deliveryId,
                    previousInvitationId = invitationId.value,
                ),
            )

            ResendInvitationResult(
                invitationId = invitationId.value,
                status = InvitationStatus.ACTIVE.name,
                expiresAt = newExpiresAt.toString(),
                version = resentInvitation.version,
            )
        }
    }

    private suspend fun resolveWorkspaceName(target: InvitationTarget, workspaceId: String?): String = when (target) {
        InvitationTarget.EXISTING_WORKSPACE -> {
            val id = requireNotNull(workspaceId) {
                "EXISTING_WORKSPACE invitation requires workspaceId"
            }
            workspaceNameReader.findName(id) ?: throw WorkspaceNotFoundException(id)
        }
        InvitationTarget.NEW_WORKSPACE -> {
            InvitationEmail.NEW_WORKSPACE_COPY_EN
        }
    }

    private fun requireResendPermission(operatorRoles: Set<PlatformRole>) {
        if (PlatformPermission.INVITATIONS_RESEND !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_RESEND)
        }
    }
}
