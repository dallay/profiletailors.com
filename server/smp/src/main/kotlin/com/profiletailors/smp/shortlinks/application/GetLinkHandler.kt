package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext

@Service
internal class GetLinkHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkRepository: LinkRepository,
    private val shortLinksProperties: ShortLinksConfigProperties,
) : QueryHandler<GetLinkQuery, LinkResult> {

    override suspend fun handle(query: GetLinkQuery): LinkResult {
        val ownerId = OwnerId.from(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId))
        val link = linkRepository.findById(LinkId(query.linkId), ownerId)
            ?: throw LinkNotFoundApplicationException(query.linkId.toString())
        return link.toResult(shortLinksProperties.shortUrlBase)
    }
}
