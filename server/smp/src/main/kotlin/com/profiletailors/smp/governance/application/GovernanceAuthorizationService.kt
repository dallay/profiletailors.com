package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.governance.domain.GovernancePermission

/**
 * Governance-specific authorization helpers.
 *
 * Provides convenience methods for checking governance permissions and
 * throwing [AuthorizationDeniedException] on denial, matching the
 * pattern established by [ConsentHandlers].
 */
@Service
internal class GovernanceAuthorizationService(private val authorizationDecider: WorkspaceAuthorizationDecider) {
    /**
     * Ensures the caller has [GovernancePermission.MEDIA_READ] in the
     * current workspace context.
     *
     * @throws AuthorizationDeniedException if permission is denied.
     */
    suspend fun authorizeMediaRead() {
        authorize(GovernancePermission.MEDIA_READ)
    }

    /**
     * Ensures the caller has [GovernancePermission.MEDIA_TAKEDOWN] in the
     * current workspace context.
     *
     * @throws AuthorizationDeniedException if permission is denied.
     */
    suspend fun authorizeMediaTakedown() {
        authorize(GovernancePermission.MEDIA_TAKEDOWN)
    }

    private suspend fun authorize(permission: GovernancePermission) {
        val decision = authorizationDecider.decideDetailed(permission.permissionKey)
        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(decision, permission.permissionKey)
        }
    }
}
