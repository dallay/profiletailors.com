package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnershipPolicy

interface WorkspaceOwnershipRepository {
    suspend fun findByWorkspaceId(workspaceId: String): Set<WorkspaceOwnership>

    suspend fun add(ownership: WorkspaceOwnership)

    suspend fun remove(workspaceId: String, principalId: String)

    suspend fun exists(workspaceId: String, principalId: String): Boolean
}

interface WorkspaceMembershipLookup {
    suspend fun resolve(principalId: String, resourceContext: ResourceContext): WorkspaceMembership?
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

class WorkspaceOwnershipOperationRequiresWorkspaceContextException : IllegalStateException(
    "Workspace ownership operations require an active workspace context.",
)
