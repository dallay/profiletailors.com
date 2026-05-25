package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus

data class UpdateWorkspaceMembershipStatusCommand(
    val targetPrincipalId: String,
    val targetStatus: WorkspaceMembershipStatus,
) : CommandWithResult<WorkspaceMembershipStatusResult>

data class WorkspaceMembershipStatusResult(
    val workspaceId: String,
    val principalId: String,
    val status: WorkspaceMembershipStatus,
)
