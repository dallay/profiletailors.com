package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.bus.command.CommandWithResult

data class AddWorkspaceOwnerCommand(
    val targetPrincipalId: String,
) : CommandWithResult<WorkspaceOwnershipResult>

data class RemoveWorkspaceOwnerCommand(
    val targetPrincipalId: String,
) : CommandWithResult<WorkspaceOwnershipResult>

data class TransferWorkspaceOwnershipCommand(
    val targetPrincipalId: String,
) : CommandWithResult<WorkspaceOwnershipResult>

data class WorkspaceOwnershipResult(
    val workspaceId: String,
    val ownerPrincipalIds: List<String>,
)

data class RenameWorkspaceCommand(
    val newName: String,
) : CommandWithResult<RenameWorkspaceResult>

data class RenameWorkspaceResult(
    val workspaceId: String,
    val name: String,
)

data class UpdateWorkspaceIconCommand(
    val icon: String?,
) : CommandWithResult<UpdateWorkspaceIconResult> {
    companion object {
        const val ICON_NAME_PATTERN = "^[a-z]([a-z-]*[a-z])?$"
    }
}

data class UpdateWorkspaceIconResult(
    val workspaceId: String,
    val icon: String?,
)
