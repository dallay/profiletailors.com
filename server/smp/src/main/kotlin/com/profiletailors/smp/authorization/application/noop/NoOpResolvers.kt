package com.profiletailors.smp.authorization.application.noop

import com.profiletailors.smp.authorization.domain.AuthorizationScope
import com.profiletailors.smp.authorization.domain.DirectGrant
import com.profiletailors.smp.authorization.domain.DirectGrantResolver
import com.profiletailors.smp.authorization.domain.Entitlement
import com.profiletailors.smp.authorization.domain.EntitlementResolver
import com.profiletailors.smp.authorization.domain.ScopeResolver
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.ResourceContext

class NoOpDirectGrantResolver : DirectGrantResolver {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<DirectGrant> = emptySet()
}

class NoOpScopeResolver : ScopeResolver {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<AuthorizationScope> = emptySet()
}

class NoOpEntitlementResolver : EntitlementResolver {
    override suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement> = emptySet()
}
