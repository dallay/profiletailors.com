package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.ResourceContext

interface ScopeResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<AuthorizationScope>
}
