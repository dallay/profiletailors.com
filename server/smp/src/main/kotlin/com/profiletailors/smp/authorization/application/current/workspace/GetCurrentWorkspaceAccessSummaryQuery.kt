package com.profiletailors.smp.authorization.application.current.workspace

import com.profiletailors.common.domain.bus.query.Query

object GetCurrentWorkspaceAccessSummaryQuery : Query<WorkspaceAccessSummary> {
    const val CURRENT_WORKSPACE_ACCESS_ENTITLEMENT: String = "workspace.access.summary"
}

data class WorkspaceAccessSummary(
    val workspaceId: String,
    val principalId: String,
    val roles: List<String>,
    val permissions: List<String>,
)
