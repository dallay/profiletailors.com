package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.RevokeWaitlistInvitationCommand
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotRevocableException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

open class RevokeWaitlistInvitationHandler(
    private val invitationRepository: WaitlistInvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(command: RevokeWaitlistInvitationCommand) {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.INVITATIONS_REVOKE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_REVOKE)
        }

        val invitation = invitationRepository.findById(WaitlistInvitationId(command.invitationId))
            ?: throw InvitationNotFoundException(command.invitationId.toString())

        if (!invitation.isActive) throw InvitationNotRevocableException(command.invitationId.toString())

        val now = clock.instant()
        invitationRepository.update(invitation.revoke(now, command.operatorPrincipalId))

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.INVITATION_REVOKED,
                targetType = "WaitlistInvitation",
                targetId = command.invitationId.toString(),
                result = AdminAuditResult.SUCCEEDED,
            ),
        )
    }
}
