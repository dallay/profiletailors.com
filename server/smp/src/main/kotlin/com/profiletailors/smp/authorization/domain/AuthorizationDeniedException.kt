package com.profiletailors.smp.authorization.domain

class AuthorizationDeniedException(message: String = "Access denied.") : IllegalStateException(message) {
    companion object {
        fun forDecision(
            decision: AuthorizationDecisionResult,
            requiredPermission: PermissionKey,
            requiredEntitlementKey: String? = null,
            targetResourceId: String? = null,
        ): AuthorizationDeniedException = when (decision.reasonCode) {
            AuthorizationReasonCode.MISSING_ENTITLEMENT ->
                AuthorizationDeniedException("Missing required entitlement ${requiredEntitlementKey ?: "unknown"}.")

            AuthorizationReasonCode.MISSING_MEMBERSHIP ->
                AuthorizationDeniedException("Active workspace membership is required.")

            AuthorizationReasonCode.SCOPE_REDUCED_TARGET ->
                AuthorizationDeniedException(
                    "Requested target ${targetResourceId ?: "unknown"} is outside the allowed scope.",
                )

            AuthorizationReasonCode.DIRECT_DENY ->
                AuthorizationDeniedException("Permission ${requiredPermission.value} was explicitly denied.")

            AuthorizationReasonCode.REVOKED_CREDENTIAL ->
                AuthorizationDeniedException("Access denied: credential has been revoked.")

            else ->
                AuthorizationDeniedException("Missing required permission ${requiredPermission.value}.")
        }
    }
}
