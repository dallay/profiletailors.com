package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyService
import com.profiletailors.smp.platformadmin.application.command.DisableUserCommand
import com.profiletailors.smp.platformadmin.application.command.EnableUserCommand
import com.profiletailors.smp.platformadmin.application.command.RevokeUserSessionsCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.handler.UserControlHandlers
import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.model.UserControlResult
import com.profiletailors.smp.platformadmin.application.model.UserSessionsRevokeResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminUsersQuery
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.platformadmin.infrastructure.persistence.ADMIN_PAGE_MAX_SIZE
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val userQuery: AdminUserQuery,
    private val operatorAccessResolver: OperatorAccessResolver,
    private val requestContextStore: RequestContextStore,
    private val auditHook: AuditHook,
    private val userControlHandlers: UserControlHandlers,
    private val userControlIdempotencyService: UserControlIdempotencyService,
) {
    @GetMapping
    suspend fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "createdAt") sort: String,
        @RequestParam(defaultValue = "desc") direction: String,
        @RequestParam status: String? = null,
        @RequestParam email: String? = null,
        @RequestParam createdFrom: Instant? = null,
        @RequestParam createdTo: Instant? = null,
    ): ResponseEntity<PagedResult<AdminUserSummary>> {
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.USERS_READ !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_READ)
        }
        if (size > ADMIN_PAGE_MAX_SIZE) return ResponseEntity.badRequest().build()
        val result = userQuery.list(
            ListAdminUsersQuery(
                page = page,
                size = size,
                sortField = sort,
                sortDirection = direction,
                status = status,
                email = email,
                createdFrom = createdFrom,
                createdTo = createdTo,
            ),
        )
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{principalId}")
    suspend fun getUser(@PathVariable principalId: String): ResponseEntity<AdminUserDetail> {
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.USERS_READ !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_READ)
        }
        val user = userQuery.findById(principalId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(user)
    }

    @PostMapping("/{principalId}/disable")
    suspend fun disableUser(
        @PathVariable principalId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): ResponseEntity<UserControlResult> {
        requireIdempotencyKey(idempotencyKey)
        val operator = resolveOperator() ?: run {
            auditRejected("USER_DISABLED", principalId)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        requireManagePermission(operator)
        return ResponseEntity.ok(
            userControlIdempotencyService.execute(
                operator.principalId,
                "disable",
                principalId,
                idempotencyKey,
                UserControlResult::class.java,
            ) {
                userControlHandlers.disable(DisableUserCommand(operator.principalId, operator.roles, principalId))
            },
        )
    }

    @PostMapping("/{principalId}/enable")
    suspend fun enableUser(
        @PathVariable principalId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): ResponseEntity<UserControlResult> {
        requireIdempotencyKey(idempotencyKey)
        val operator = resolveOperator() ?: run {
            auditRejected("USER_ENABLED", principalId)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        requireManagePermission(operator)
        return ResponseEntity.ok(
            userControlIdempotencyService.execute(
                operator.principalId,
                "enable",
                principalId,
                idempotencyKey,
                UserControlResult::class.java,
            ) {
                userControlHandlers.enable(EnableUserCommand(operator.principalId, operator.roles, principalId))
            },
        )
    }

    @PostMapping("/{principalId}/sessions/revoke")
    suspend fun revokeUserSessions(
        @PathVariable principalId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): ResponseEntity<UserSessionsRevokeResult> {
        requireIdempotencyKey(idempotencyKey)
        val operator = resolveOperator() ?: run {
            auditRejected("USER_SESSIONS_REVOKED", principalId)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        requireManagePermission(operator)
        return ResponseEntity.ok(
            userControlIdempotencyService.execute(
                operator.principalId,
                "sessions_revoke",
                principalId,
                idempotencyKey,
                UserSessionsRevokeResult::class.java,
            ) {
                userControlHandlers.revokeSessions(
                    RevokeUserSessionsCommand(operator.principalId, operator.roles, principalId),
                )
            },
        )
    }

    @GetMapping("/{principalId}/workspaces")
    suspend fun getUserWorkspaces(
        @PathVariable principalId: String,
    ): ResponseEntity<List<AdminWorkspaceMembershipSummary>> {
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val permissions = operator.roles.effectivePermissions()
        if (PlatformPermission.USERS_READ !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_READ)
        }
        if (PlatformPermission.USERS_WORKSPACES_READ !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_WORKSPACES_READ)
        }
        if (userQuery.findById(principalId) == null) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(userQuery.findWorkspacesByPrincipalId(principalId))
    }

    private suspend fun auditRejected(action: String, targetPrincipalId: String) {
        auditHook.onMutation(
            MutationAuditFact(
                action = action,
                targetType = "USER",
                targetId = targetPrincipalId,
                actorPrincipalId = "UNAUTHENTICATED",
                workspaceId = null,
                outcome = MutationAuditOutcome.REJECTED,
                details = mapOf("reason" to "Authentication required."),
            ),
        )
    }

    private fun requireIdempotencyKey(value: String) {
        require(
            value.length in IDEMPOTENCY_KEY_MIN_LENGTH..IDEMPOTENCY_KEY_MAX_LENGTH &&
                value.all { it.isLetterOrDigit() || it in "._:-" },
        )
    }

    private fun requireManagePermission(operator: com.profiletailors.smp.platformadmin.application.OperatorAccess) {
        if (PlatformPermission.USERS_MANAGE !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.USERS_MANAGE)
        }
    }

    private suspend fun resolveOperator(): com.profiletailors.smp.platformadmin.application.OperatorAccess? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        return operatorAccessResolver.resolve(ctx)
    }

    private companion object {
        const val IDEMPOTENCY_KEY_MIN_LENGTH = 1
        const val IDEMPOTENCY_KEY_MAX_LENGTH = 128
    }
}
