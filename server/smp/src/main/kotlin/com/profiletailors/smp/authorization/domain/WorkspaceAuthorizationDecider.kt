package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.context.ResourceContext

interface WorkspaceAuthorizationDecider {
    suspend fun decide(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String? = null,
        resourceContextOverride: ResourceContext? = null,
    ): AuthorizationDecision

    suspend fun decideDetailed(
        requiredPermission: PermissionKey,
        requiredEntitlementKey: String? = null,
        resourceContextOverride: ResourceContext? = null,
    ): AuthorizationDecisionResult
}
