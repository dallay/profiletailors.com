package com.profiletailors.smp.authorization.domain

import com.profiletailors.smp.tenancy.domain.WorkspaceMembership


interface WorkspaceMembershipRoleResolver {
    suspend fun resolve(membership: WorkspaceMembership): Set<Role>
}
