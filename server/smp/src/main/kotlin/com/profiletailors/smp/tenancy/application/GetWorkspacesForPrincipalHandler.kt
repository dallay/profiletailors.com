package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.tenancy.infrastructure.R2dbcWorkspaceReadRepository

/**
 * Handler for [GetWorkspacesForPrincipalQuery].
 *
 * Resolves the authenticated principal from the security context and delegates
 * to the read repository to fetch all workspaces the user belongs to.
 * This does NOT require an X-Workspace-Id header — it lists workspaces
 * across all contexts for the current user.
 */
@Service
internal class GetWorkspacesForPrincipalHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val workspaceReadRepository: R2dbcWorkspaceReadRepository,
) : QueryHandler<GetWorkspacesForPrincipalQuery, List<WorkspaceSummary>> {

    override suspend fun handle(query: GetWorkspacesForPrincipalQuery): List<WorkspaceSummary> {
        val principalContext = principalContextProvider.require()
        return workspaceReadRepository.findWorkspacesByPrincipal(principalContext.principalId)
    }
}
