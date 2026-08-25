package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.AssignPlatformRoleCommand
import com.profiletailors.smp.platformadmin.application.command.RevokePlatformRoleCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

open class AssignPlatformRoleHandler(
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(command: AssignPlatformRoleCommand) {
        if (PlatformPermission.OPERATORS_MANAGE !in command.operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.OPERATORS_MANAGE)
        }

        val now = clock.instant()
        roleAssignmentRepository.save(
            PlatformRoleAssignment(
                id = PlatformRoleAssignmentId.generate(),
                principalId = command.targetPrincipalId,
                role = command.role,
                assignedAt = now,
                assignedBy = command.operatorPrincipalId,
            ),
        )

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.PLATFORM_ROLE_ASSIGNED,
                targetType = "Principal",
                targetId = command.targetPrincipalId.toString(),
                result = AdminAuditResult.SUCCEEDED,
                metadata = mapOf("role" to command.role.name),
            ),
        )
    }
}

open class RevokePlatformRoleHandler(
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun handle(command: RevokePlatformRoleCommand) {
        if (PlatformPermission.OPERATORS_MANAGE !in command.operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.OPERATORS_MANAGE)
        }

        val activeAssignments = roleAssignmentRepository.findActiveByPrincipalId(command.targetPrincipalId)
        val toRevoke = activeAssignments.filter { it.role == command.role }

        if (toRevoke.isEmpty()) return

        val now = clock.instant()
        toRevoke.forEach { assignment ->
            roleAssignmentRepository.update(assignment.revoke(now, command.operatorPrincipalId))
        }

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.PLATFORM_ROLE_REVOKED,
                targetType = "Principal",
                targetId = command.targetPrincipalId.toString(),
                result = AdminAuditResult.SUCCEEDED,
                metadata = mapOf("role" to command.role.name),
            ),
        )
    }
}
