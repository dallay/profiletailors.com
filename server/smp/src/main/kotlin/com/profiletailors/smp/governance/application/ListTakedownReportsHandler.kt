package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import kotlinx.coroutines.flow.Flow

/**
 * Handles listing takedown reports for the current workspace.
 *
 * 1. Authorizes the caller (requires MEDIA_READ permission).
 * 2. Delegates to the repository with optional status filter.
 */
@Service
internal class ListTakedownReportsHandler(
    private val repository: TakedownReportRepository,
    private val resourceContextProvider: ResourceContextProvider,
    private val authorizationService: GovernanceAuthorizationService,
) : QueryHandler<ListTakedownReportsQuery, Flow<TakedownReport>> {

    override suspend fun handle(query: ListTakedownReportsQuery): Flow<TakedownReport> {
        authorizationService.authorizeMediaRead()

        val workspaceId = requireNotNull(resourceContextProvider.require().workspaceId) {
            "Workspace ID is required to list takedown reports"
        }
        return repository.findByWorkspace(workspaceId, query.status)
    }
}
