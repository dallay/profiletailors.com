package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.identity.domain.PrincipalStatus
import com.profiletailors.smp.platformadmin.application.command.ReactivateUserCommand
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

class ReactivateUserHandler(
    private val principalAdmin: PrincipalAdmin,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(command: ReactivateUserCommand) {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.USERS_REACTIVATE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_REACTIVATE)
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
        if (principal.status == PrincipalStatus.ACTIVE) {
            return
        }
        principalAdmin.updateStatus(command.principalId, PrincipalStatus.ACTIVE)
        val now = clock.instant()
        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.USER_REACTIVATED,
                targetType = "Principal",
                targetId = command.principalId,
                result = AdminAuditResult.SUCCEEDED,
            ),
        )
    }
}
