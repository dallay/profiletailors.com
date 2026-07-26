package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType

internal class PublishingOperationRequiresWorkspaceContextException :
    IllegalStateException("Workspace context is required for this publishing operation.")

internal fun ResourceContextProvider.requireWorkspaceContext(): ResourceContext {
    val resourceContext = require()
    if (resourceContext.type != ResourceContextType.WORKSPACE || resourceContext.workspaceId.isNullOrBlank()) {
        throw PublishingOperationRequiresWorkspaceContextException()
    }
    return resourceContext
}
