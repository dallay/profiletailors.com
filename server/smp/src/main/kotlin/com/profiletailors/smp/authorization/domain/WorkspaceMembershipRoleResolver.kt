package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot


fun interface WorkspaceMembershipRoleResolver {
    suspend fun resolve(membership: WorkspaceMembershipSnapshot): Set<Role>
}
