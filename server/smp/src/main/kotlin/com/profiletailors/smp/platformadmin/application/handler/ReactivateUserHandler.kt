package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.identity.application.PrincipalLifecycle
import com.profiletailors.smp.identity.application.PrincipalStatusTransition
import com.profiletailors.smp.platformadmin.application.command.ReactivateUserCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

class ReactivateUserHandler(
    private val principalLifecycleService: PrincipalLifecycle,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(command: ReactivateUserCommand) {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.USERS_REACTIVATE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_REACTIVATE)
        }
        val transition = principalLifecycleService.reactivate(command.principalId, command.expectedVersion)
        if (transition != PrincipalStatusTransition.TRANSITIONED) return
        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = clock.instant(),
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.USER_ENABLED,
                targetType = "Principal",
                targetId = command.principalId,
                result = AdminAuditResult.SUCCEEDED,
            ),
        )
    }
}
