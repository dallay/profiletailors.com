package com.profiletailors.smp.tenancy.application

import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType

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
