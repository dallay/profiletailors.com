package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.identity.domain.PrincipalStatus
import com.profiletailors.smp.platformadmin.application.command.DeactivateUserCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.PrincipalAdmin
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.UserAccountDeactivationConflictException
import com.profiletailors.smp.platformadmin.domain.UserPrincipalNotFoundException
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

class DeactivateUserHandler(
    private val principalAdmin: PrincipalAdmin,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(command: DeactivateUserCommand) {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.USERS_DEACTIVATE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_DEACTIVATE)
        }
        val principal = principalAdmin.findById(command.principalId)
            ?: throw UserPrincipalNotFoundException(command.principalId)
        if (principal.version != command.expectedVersion) {
            throw UserAccountDeactivationConflictException(
                command.principalId,
                command.expectedVersion,
                principal.version,
            )
        }
        if (principal.status == PrincipalStatus.DEACTIVATED || principal.status == PrincipalStatus.SUSPENDED) {
            return
        }
        principalAdmin.updateStatus(command.principalId, PrincipalStatus.DEACTIVATED)
        val now = clock.instant()
        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.USER_DEACTIVATED,
                targetType = "Principal",
                targetId = command.principalId,
                result = AdminAuditResult.SUCCEEDED,
            ),
        )
    }
}
