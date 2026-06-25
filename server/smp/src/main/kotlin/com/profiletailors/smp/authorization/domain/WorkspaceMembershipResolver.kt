package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot

fun interface WorkspaceMembershipResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): WorkspaceMembershipSnapshot?
}
