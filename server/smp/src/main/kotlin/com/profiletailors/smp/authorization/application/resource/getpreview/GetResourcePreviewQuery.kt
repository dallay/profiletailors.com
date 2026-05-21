package com.profiletailors.smp.authorization.application.resource.getpreview

import com.profiletailors.common.domain.bus.query.Query


data class GetResourcePreviewQuery(
    val resourceId: String,
) : Query<ResourcePreview>

data class ResourcePreview(
    val workspaceId: String,
    val resourceId: String,
    val principalId: String,
    val previewAllowed: Boolean,
)

