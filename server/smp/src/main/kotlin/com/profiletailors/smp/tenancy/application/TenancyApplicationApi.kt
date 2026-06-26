package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext

fun interface ActiveWorkspaceContextResolver {
    fun resolve(workspaceId: String?): ResourceContext
}

class MissingActiveWorkspaceException(message: String = "Active workspace identifier is required.") :
    IllegalArgumentException(message)

class WorkspaceOwnershipOperationRequiresWorkspaceContextException :
    IllegalStateException(
        "Workspace ownership operations require an active workspace context.",
    )

class WorkspaceOwnerAccessDeniedException :
    IllegalStateException(
        "Only a current workspace owner may manage workspace ownership.",
    )

class WorkspaceOwnerNotFoundException(principalId: String, workspaceId: String) :
    IllegalStateException(
        "Owner '$principalId' was not found for workspace '$workspaceId'.",
    )

class OwnerTargetMustBeActiveMemberException(principalId: String, workspaceId: String) :
    IllegalStateException(
        "Principal '$principalId' must be an active member of workspace '$workspaceId' to hold ownership.",
    )

class WorkspaceMembershipNotFoundException(principalId: String, workspaceId: String) :
    IllegalStateException(
        "Membership for principal '$principalId' was not found in workspace '$workspaceId'.",
    )
