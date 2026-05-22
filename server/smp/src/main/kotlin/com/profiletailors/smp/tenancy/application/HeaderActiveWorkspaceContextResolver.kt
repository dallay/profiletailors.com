package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.tenancy.application.ActiveWorkspaceContextResolver
import com.profiletailors.smp.tenancy.application.MissingActiveWorkspaceException

internal class HeaderActiveWorkspaceContextResolver : ActiveWorkspaceContextResolver {
    override fun resolve(workspaceId: String?): ResourceContext {
        val normalizedWorkspaceId = workspaceId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw MissingActiveWorkspaceException()

        return ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = normalizedWorkspaceId,
        )
    }
}
