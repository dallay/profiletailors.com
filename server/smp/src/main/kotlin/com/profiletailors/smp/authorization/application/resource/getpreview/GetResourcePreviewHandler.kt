package com.profiletailors.smp.authorization.application.resource.getpreview

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler

@Service
class GetResourcePreviewHandler(
    private val service: GetResourcePreviewService,
) : QueryHandler<GetResourcePreviewQuery, ResourcePreview> {

    override suspend fun handle(query: GetResourcePreviewQuery): ResourcePreview =
        service.execute(query)
}
