package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.contracts.AdminOperatorQuery
import com.profiletailors.smp.platformadmin.application.handler.AssignPlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokePlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.model.AdminOperatorSummary
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
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
    private val operatorQuery: AdminOperatorQuery,
    private val operatorAccessResolver: OperatorAccessResolver,
    private val assignRoleHandler: AssignPlatformRoleHandler,
    private val revokeRoleHandler: RevokePlatformRoleHandler,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping
    suspend fun listOperators(): ResponseEntity<List<AdminOperatorSummary>> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        if (PlatformPermission.OPERATORS_READ !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.OPERATORS_READ)
        }

        return ResponseEntity.ok(operatorQuery.listAllActive())
    }

    @PostMapping("/{principalId}/roles")
    @Transactional
    suspend fun assignRole(
        @PathVariable principalId: UUID,
        @RequestBody request: AssignRoleRequest,
    ): ResponseEntity<Map<String, String>> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val role = runCatching { PlatformRole.valueOf(request.role) }.getOrNull()
            ?: return ResponseEntity.badRequest().build()

        assignRoleHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.AssignPlatformRoleCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                targetPrincipalId = principalId,
                role = role,
            ),
        )
        return ResponseEntity.ok(mapOf("status" to "assigned"))
    }

    @DeleteMapping("/{principalId}/roles/{role}")
    @Transactional
    suspend fun revokeRole(
        @PathVariable principalId: UUID,
        @PathVariable role: String,
    ): ResponseEntity<Map<String, String>> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val platformRole = runCatching { PlatformRole.valueOf(role) }.getOrNull()
            ?: return ResponseEntity.badRequest().build()

        revokeRoleHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.RevokePlatformRoleCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                targetPrincipalId = principalId,
                role = platformRole,
            ),
        )
        return ResponseEntity.ok(mapOf("status" to "revoked"))
    }

    private suspend fun resolveOperator(): com.profiletailors.smp.platformadmin.application.OperatorAccess? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        return operatorAccessResolver.resolve(ctx)
    }

    data class AssignRoleRequest(val role: String)
}
