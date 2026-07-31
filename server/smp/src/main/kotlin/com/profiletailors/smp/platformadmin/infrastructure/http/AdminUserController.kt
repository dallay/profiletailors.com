package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.ports.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.query.ListAdminUsersQuery
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val userQuery: AdminUserQuery,
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping
    suspend fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "createdAt") sort: String,
        @RequestParam(defaultValue = "desc") direction: String,
        @RequestParam status: String? = null,
        @RequestParam email: String? = null,
        @RequestParam authenticationMethod: String? = null,
        @RequestParam createdFrom: Instant? = null,
        @RequestParam createdTo: Instant? = null,
    ): ResponseEntity<PagedResult<AdminUserSummary>> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.USERS_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val result = userQuery.list(
            ListAdminUsersQuery(
                page = page,
                size = size,
                sortField = sort,
                sortDirection = direction,
                status = status,
                email = email,
                authenticationMethod = authenticationMethod,
                createdFrom = createdFrom,
                createdTo = createdTo,
            ),
        )
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{principalId}")
    suspend fun getUser(@PathVariable principalId: String): ResponseEntity<AdminUserDetail> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.USERS_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val user = userQuery.findById(principalId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(user)
    }

    @GetMapping("/{principalId}/workspaces")
    suspend fun getUserWorkspaces(
        @PathVariable principalId: String,
    ): ResponseEntity<List<AdminWorkspaceMembershipSummary>> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.USERS_WORKSPACES_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(userQuery.findWorkspacesByPrincipalId(principalId))
    }

    private suspend fun resolveOperator(): Pair<UUID, Set<PlatformRole>>? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        val operatorId = UUID.fromString(ctx.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        return operatorId to assignments.map { it.role }.toSet()
    }
}
