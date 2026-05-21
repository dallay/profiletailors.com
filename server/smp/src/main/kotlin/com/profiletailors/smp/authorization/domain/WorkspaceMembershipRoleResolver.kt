package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot


interface WorkspaceMembershipRoleResolver {
    suspend fun resolve(membership: WorkspaceMembershipSnapshot): Set<Role>
}
