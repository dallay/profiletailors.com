package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service

/**
 * Governance-specific authorization helpers.
 *
 * Provides convenience methods for checking governance permissions, matching the
 * pattern established by [ConsentHandlers].
 */
@Service
internal class GovernanceAuthorizationService(private val authorizationGate: GovernanceAuthorizationGate) {
    /**
     * Ensures the caller has [GovernancePermission.MEDIA_READ] in the
     * current workspace context.
     *
     * @throws AuthorizationDeniedException if permission is denied.
     */
    suspend fun authorizeMediaRead() {
        authorize(GovernanceAuthorizationPermission.MEDIA_READ)
    }

    /**
     * Ensures the caller has [GovernancePermission.MEDIA_TAKEDOWN] in the
     * current workspace context.
     *
     * @throws AuthorizationDeniedException if permission is denied.
     */
    suspend fun authorizeMediaTakedown() {
        authorize(GovernanceAuthorizationPermission.MEDIA_TAKEDOWN)
    }

    suspend fun authorizeConsentRead() {
        authorize(GovernanceAuthorizationPermission.CONSENT_READ)
    }

    suspend fun authorizeConsentWrite() {
        authorize(GovernanceAuthorizationPermission.CONSENT_WRITE)
    }

    suspend fun authorizeAuditRead() {
        authorize(GovernanceAuthorizationPermission.AUDIT_READ)
    }

    private suspend fun authorize(permission: GovernanceAuthorizationPermission) {
        authorizationGate.requireAllowed(permission)
    }
}
