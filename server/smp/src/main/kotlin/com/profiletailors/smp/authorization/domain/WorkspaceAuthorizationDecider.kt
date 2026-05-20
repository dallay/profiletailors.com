package com.profiletailors.smp.authorization.domain

import com.profiletailors.smp.authorization.application.AuthorizationDecisionResult
import com.profiletailors.smp.platform.domain.ResourceContext

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
