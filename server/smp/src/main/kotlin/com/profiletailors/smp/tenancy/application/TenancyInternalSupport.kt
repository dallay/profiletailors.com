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

    /**
     * Returns the principal IDs of every workspace owner without materialising the full
     * [WorkspaceOwnership] entity. Use this from contexts that only need identity to send
     * notifications or run lookups — the entity itself MUST stay inside the tenancy aggregate.
     */
    suspend fun findOwnerIds(workspaceId: String): Set<String>

    suspend fun add(ownership: WorkspaceOwnership)

    suspend fun remove(workspaceId: String, principalId: String)

    /**
     * Removes [principalId] from [workspaceId] ownership only when at least one other owner
     * already exists at the time of deletion, making the check-and-delete atomic.
     *
     * This eliminates the TOCTOU window that exists when a caller reads owners, validates the
     * count, and deletes in separate steps: another concurrent transaction could remove an owner
     * between the read and the delete, leaving the workspace ownerless.
     *
     * @return `true` if the ownership row was deleted; `false` if the row was not deleted
     *   because no replacement owner exists (i.e. [principalId] is the only owner), **or**
     *   because the ownership row no longer exists at delete time (another concurrent
     *   operation already removed it).  Callers that receive `false` must treat it as a
     *   signal that the operation cannot proceed, regardless of the reason.
     */
    suspend fun removeIfReplacementExists(workspaceId: String, principalId: String): Boolean

    suspend fun exists(workspaceId: String, principalId: String): Boolean
}

internal fun interface WorkspaceMembershipLookup {
    suspend fun resolve(principalId: String, resourceContext: ResourceContext): WorkspaceMembership?
}

internal fun interface WorkspaceMembershipAccessChecker {
    suspend fun isActiveMember(principalId: String, resourceContext: ResourceContext): Boolean
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
