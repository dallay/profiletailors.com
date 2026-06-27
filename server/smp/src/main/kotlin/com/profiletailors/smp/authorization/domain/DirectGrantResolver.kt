package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.ResourceContext

fun interface DirectGrantResolver {
    suspend fun resolve(principalContext: PrincipalContext, resourceContext: ResourceContext): Set<DirectGrant>
}
