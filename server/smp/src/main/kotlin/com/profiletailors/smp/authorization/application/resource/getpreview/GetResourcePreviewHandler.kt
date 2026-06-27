package com.profiletailors.smp.authorization.application.resource.getpreview

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.authorization.application.resource.getpreview.GetResourcePreviewQuery
import com.profiletailors.smp.authorization.application.resource.getpreview.ResourcePreview

@Service
internal class GetResourcePreviewHandler(private val service: GetResourcePreviewService) :
    QueryHandler<GetResourcePreviewQuery, ResourcePreview> {

    override suspend fun handle(query: GetResourcePreviewQuery): ResourcePreview = service.execute(query)
}
