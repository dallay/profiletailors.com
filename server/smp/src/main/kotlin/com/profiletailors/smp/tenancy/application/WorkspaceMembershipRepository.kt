package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus

interface WorkspaceMembershipRepository {
    suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceMembership>

    suspend fun updateStatus(
        workspaceId: String,
        principalId: String,
        status: WorkspaceMembershipStatus,
    )
}
