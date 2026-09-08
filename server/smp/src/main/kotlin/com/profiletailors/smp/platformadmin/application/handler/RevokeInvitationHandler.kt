package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.RevokeInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.application.result.RevokeInvitationResult
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotRevocableException
import com.profiletailors.smp.platformadmin.domain.InvitationVersionConflictException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.time.Instant
import java.util.UUID

class RevokeInvitationHandler(
    private val invitationRepository: InvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
    private val telemetry: InvitationTelemetry,
) {
    suspend fun handle(command: RevokeInvitationCommand): RevokeInvitationResult {
        requireRevokePermission(command.operatorRoles)
        val now = clock.instant()
        val invitation = loadActiveInvitation(command.invitationId, now)
        val revoked = invitation.revoke(command.expectedVersion)

        if (!invitationRepository.updateIfVersionMatches(revoked)) {
            throw InvitationVersionConflictException(command.invitationId.toString())
        }
        telemetry.recordInvitationRevoked()

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = java.util.UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.INVITATION_REVOKED,
                targetType = "Invitation",
                targetId = command.invitationId.toString(),
                result = AdminAuditResult.SUCCEEDED,
            ),
        )

        return RevokeInvitationResult(invitationId = command.invitationId)
    }

    private fun requireRevokePermission(operatorRoles: Set<PlatformRole>) {
        if (PlatformPermission.INVITATIONS_REVOKE !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_REVOKE)
        }
    }

    private suspend fun loadActiveInvitation(invitationId: UUID, now: Instant): Invitation {
        val invitation = invitationRepository.findById(InvitationId(invitationId))
            ?: throw InvitationNotFoundException(invitationId.toString())
        if (!invitation.isActive(now)) {
            throw InvitationNotRevocableException(invitationId.toString())
        }
        return invitation
    }
}
