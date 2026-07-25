package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.tenancy.application.TenancyAuthorizationGate
import com.profiletailors.smp.tenancy.application.TenancyAuthorizationPermission
import org.springframework.stereotype.Component

@Component
internal class WorkspaceAuthorizationTenancyGate(
    private val workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
) : TenancyAuthorizationGate {

    override suspend fun requireAllowed(permission: TenancyAuthorizationPermission) {
        val requiredPermission = permission.toPermissionKey()
        val decision = workspaceAuthorizationDecider.decideDetailed(requiredPermission)

        // Preserve previous behavior: only explicit DENY blocks the operation.
        if (decision.decision == AuthorizationDecision.DENY) {
            throw AuthorizationDeniedException.forDecision(decision, requiredPermission)
        }
    }

    private fun TenancyAuthorizationPermission.toPermissionKey(): PermissionKey = when (this) {
        TenancyAuthorizationPermission.WORKSPACE_SETTINGS_MANAGE ->
            PermissionKey.of("workspace", "settings", "manage")
    }
}