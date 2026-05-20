package com.profiletailors.smp.authorization.application.resource.getpreview

import com.profiletailors.smp.platform.application.Query


data class GetResourcePreviewQuery(
    val resourceId: String,
) : Query<ResourcePreview>

data class ResourcePreview(
    val workspaceId: String,
    val resourceId: String,
    val principalId: String,
    val previewAllowed: Boolean,
)

