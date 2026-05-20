package com.profiletailors.smp.authorization.domain

import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership

interface WorkspaceMembershipResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): WorkspaceMembership?
}
