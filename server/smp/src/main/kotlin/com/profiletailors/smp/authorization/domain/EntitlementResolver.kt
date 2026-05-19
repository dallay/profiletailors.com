package com.profiletailors.smp.authorization.domain

import com.profiletailors.smp.platform.domain.ResourceContext

interface EntitlementResolver {
    suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement>
}
