package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.governance.application.GovernanceAuthorizationGate
import com.profiletailors.smp.governance.application.GovernanceAuthorizationPermission
import org.springframework.stereotype.Component

@Component
internal class WorkspaceAuthorizationGovernanceGate(
    private val authorizationDecider: WorkspaceAuthorizationDecider,
) : GovernanceAuthorizationGate {

    override suspend fun requireAllowed(permission: GovernanceAuthorizationPermission) {
        val requiredPermission = permission.toPermissionKey()
        val decision = authorizationDecider.decideDetailed(requiredPermission = requiredPermission)
        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(
                decision = decision,
                requiredPermission = requiredPermission,
            )
        }
    }

    private fun GovernanceAuthorizationPermission.toPermissionKey(): PermissionKey = when (this) {
        GovernanceAuthorizationPermission.MEDIA_READ -> PermissionKey.of("workspace", "governance", "media-read")
        GovernanceAuthorizationPermission.MEDIA_TAKEDOWN -> PermissionKey.of("workspace", "governance", "media-takedown")
        GovernanceAuthorizationPermission.CONSENT_READ -> PermissionKey.of("workspace", "consent", "read")
        GovernanceAuthorizationPermission.CONSENT_WRITE -> PermissionKey.of("workspace", "consent", "write")
        GovernanceAuthorizationPermission.AUDIT_READ -> PermissionKey.of("workspace", "audit", "read")
    }
}