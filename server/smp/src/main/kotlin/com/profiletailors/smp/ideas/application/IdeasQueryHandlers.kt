package com.profiletailors.smp.ideas.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.authorization.application.WorkspaceMembershipGate
import com.profiletailors.smp.ideas.domain.IdeaBoardConfigRepository
import com.profiletailors.smp.ideas.domain.IdeaBoardDefaults
import com.profiletailors.smp.ideas.domain.IdeaPolicies
import com.profiletailors.smp.ideas.domain.IdeaRepository
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext

@Service
internal class ListIdeasHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val ideaRepository: IdeaRepository,
    private val membershipGate: WorkspaceMembershipGate,
) : QueryHandler<ListIdeasQuery, ListIdeasResponse> {
    override suspend fun handle(query: ListIdeasQuery): ListIdeasResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        membershipGate.requireActiveMember(workspaceId)
        val ideas = IdeaPolicies.normalizeIdeasInColumns(ideaRepository.listByWorkspace(workspaceId))
        return ListIdeasResponse(ideas = ideas.map { it.toResult() })
    }
}

@Service
internal class GetIdeaHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val ideaRepository: IdeaRepository,
    private val membershipGate: WorkspaceMembershipGate,
) : QueryHandler<GetIdeaQuery, IdeaResult> {
    override suspend fun handle(query: GetIdeaQuery): IdeaResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        membershipGate.requireActiveMember(workspaceId)
        val idea = ideaRepository.findByWorkspaceAndId(workspaceId, query.ideaId)
            ?: throw IdeaNotFoundException(query.ideaId)
        return idea.toResult()
    }
}

@Service
internal class GetColumnsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val boardConfigRepository: IdeaBoardConfigRepository,
    private val membershipGate: WorkspaceMembershipGate,
) : QueryHandler<GetColumnsQuery, ColumnsResponse> {
    override suspend fun handle(query: GetColumnsQuery): ColumnsResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        membershipGate.requireActiveMember(workspaceId)
        val config = boardConfigRepository.findByWorkspace(workspaceId)
        val columns = config?.columns ?: IdeaBoardDefaults.columns
        return ColumnsResponse(IdeaPolicies.normalizeColumns(columns))
    }
}
