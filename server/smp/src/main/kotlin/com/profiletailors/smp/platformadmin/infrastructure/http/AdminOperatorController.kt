package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.handler.AssignPlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokePlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.model.AdminOperatorSummary
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/operators")
class AdminOperatorController(
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val assignRoleHandler: AssignPlatformRoleHandler,
    private val revokeRoleHandler: RevokePlatformRoleHandler,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping
    suspend fun listOperators(): ResponseEntity<List<AdminOperatorSummary>> {
        val ctx = requestContextStore.currentPrincipalContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val operatorId = UUID.fromString(ctx.principalId)
        val activeAssignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        val operatorRoles = activeAssignments.map { it.role }.toSet()

        if (PlatformPermission.OPERATORS_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val allAssignments = roleAssignmentRepository.findAllActive()
        val grouped = allAssignments.groupBy { it.principalId }
        val result = grouped.map { (principalId, assignments) ->
            AdminOperatorSummary(
                principalId = principalId,
                email = "",
                displayName = null,
                platformRoles = assignments.map { it.role.name },
                assignedAt = assignments.minOf { it.assignedAt },
            )
        }
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{principalId}/roles")
    @Transactional
    suspend fun assignRole(
        @PathVariable principalId: UUID,
        @RequestBody request: AssignRoleRequest,
    ): ResponseEntity<Unit> {
        val (operatorId, operatorRoles) = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val role = runCatching { PlatformRole.valueOf(request.role) }.getOrNull()
            ?: return ResponseEntity.badRequest().build()

        assignRoleHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.AssignPlatformRoleCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                targetPrincipalId = principalId,
                role = role,
            ),
        )
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @DeleteMapping("/{principalId}/roles/{role}")
    @Transactional
    suspend fun revokeRole(@PathVariable principalId: UUID, @PathVariable role: String): ResponseEntity<Unit> {
        val (operatorId, operatorRoles) = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val platformRole = runCatching { PlatformRole.valueOf(role) }.getOrNull()
            ?: return ResponseEntity.badRequest().build()

        revokeRoleHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.RevokePlatformRoleCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                targetPrincipalId = principalId,
                role = platformRole,
            ),
        )
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    private suspend fun resolveOperator(): Pair<UUID, Set<PlatformRole>>? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        val operatorId = UUID.fromString(ctx.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        val roles = assignments.map { it.role }.toSet()
        return operatorId to roles
    }

    data class AssignRoleRequest(val role: String)
}
