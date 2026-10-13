package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.shortlinks.domain.LinkClickMetricsRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext

@Service
internal class GetLinkMetricsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkClickMetricsRepository: LinkClickMetricsRepository,
) : QueryHandler<GetLinkMetricsQuery, Long> {
    override suspend fun handle(query: GetLinkMetricsQuery): Long {
        val ownerId = OwnerId.from(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId))
        return linkClickMetricsRepository.countByLinkIdAndOwner(LinkId(query.linkId), ownerId)
            ?: throw LinkNotFoundApplicationException(query.linkId.toString())
    }
}
