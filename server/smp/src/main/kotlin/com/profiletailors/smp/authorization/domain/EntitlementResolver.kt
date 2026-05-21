package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.context.ResourceContext

interface EntitlementResolver {
    suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement>
}
