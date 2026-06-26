package com.profiletailors.smp.authorization.application.current.workspace

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.authorization.application.current.workspace.GetCurrentWorkspaceAccessSummaryQuery
import com.profiletailors.smp.authorization.application.current.workspace.WorkspaceAccessSummary

@Service
internal class GetCurrentWorkspaceAccessSummaryHandler(private val service: GetCurrentWorkspaceAccessSummaryService) :
    QueryHandler<GetCurrentWorkspaceAccessSummaryQuery, WorkspaceAccessSummary> {

    override suspend fun handle(query: GetCurrentWorkspaceAccessSummaryQuery): WorkspaceAccessSummary =
        service.execute(query)
}
