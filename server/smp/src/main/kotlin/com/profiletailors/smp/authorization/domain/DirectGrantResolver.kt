package com.profiletailors.smp.authorization.domain

import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.domain.ResourceContext

interface DirectGrantResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<DirectGrant>
}
