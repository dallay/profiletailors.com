package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot

interface WorkspaceMembershipProvisioner {
    suspend fun reconcile(workspaceId: String, principalId: String): WorkspaceMembershipSnapshot
}

class WorkspaceMembershipProvisionerAdapter(private val repository: WorkspaceMembershipRepository) :
    WorkspaceMembershipProvisioner {
    override suspend fun reconcile(workspaceId: String, principalId: String): WorkspaceMembershipSnapshot =
        repository.reconcile(workspaceId, principalId)
}
