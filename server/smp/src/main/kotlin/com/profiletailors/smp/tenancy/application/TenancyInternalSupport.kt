package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnershipPolicy

internal interface WorkspaceOwnershipRepository {
    suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceOwnership>

    suspend fun add(ownership: WorkspaceOwnership)

    suspend fun remove(workspaceId: String, principalId: String)

    suspend fun exists(workspaceId: String, principalId: String): Boolean
}

internal interface WorkspaceMembershipLookup {
    suspend fun resolve(principalId: String, resourceContext: ResourceContext): WorkspaceMembership?
}

internal interface WorkspaceMembershipRepository {
    suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceMembership>

    suspend fun updateStatus(
        workspaceId: String,
        principalId: String,
        status: com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus,
    )
}

internal fun ResourceContextProvider.requireWorkspaceContext(): ResourceContext {
    val resourceContext = require()
    if (resourceContext.type != ResourceContextType.WORKSPACE || resourceContext.workspaceId.isNullOrBlank()) {
        throw WorkspaceOwnershipOperationRequiresWorkspaceContextException()
    }
    return resourceContext
}

internal suspend fun WorkspaceOwnershipRepository.requireCurrentOwners(workspaceId: String): Set<WorkspaceOwnership> =
    findByWorkspaceId(workspaceId)
        .also { owners -> WorkspaceOwnershipPolicy().ensureAtLeastOneOwner(owners) }
