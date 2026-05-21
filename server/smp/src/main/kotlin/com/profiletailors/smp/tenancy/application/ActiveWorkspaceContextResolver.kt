package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType

interface ActiveWorkspaceContextResolver {
    fun resolve(workspaceId: String?): ResourceContext
}

class HeaderActiveWorkspaceContextResolver : ActiveWorkspaceContextResolver {
    override fun resolve(workspaceId: String?): ResourceContext {
        val normalizedWorkspaceId = workspaceId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw MissingActiveWorkspaceException()

        return ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = normalizedWorkspaceId,
        )
    }
}

class MissingActiveWorkspaceException(
    message: String = "Active workspace identifier is required.",
) : IllegalArgumentException(message)
