package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import java.util.UUID

internal class HeaderActiveWorkspaceContextResolver : ActiveWorkspaceContextResolver {
    override fun resolve(workspaceId: String?): ResourceContext {
        val normalizedWorkspaceId = workspaceId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw MissingActiveWorkspaceException()

        val parsedWorkspaceId = runCatching { UUID.fromString(normalizedWorkspaceId) }
            .getOrNull()
            ?.takeIf { it.toString() == normalizedWorkspaceId.lowercase() }
            ?: throw InvalidActiveWorkspaceException()

        return ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = parsedWorkspaceId.toString(),
        )
    }
}
