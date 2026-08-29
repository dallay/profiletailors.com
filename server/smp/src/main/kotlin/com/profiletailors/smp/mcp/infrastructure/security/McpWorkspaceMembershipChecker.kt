package com.profiletailors.smp.mcp.infrastructure.security

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipAccessChecker
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

@Service
internal class McpWorkspaceMembershipChecker(private val accessChecker: WorkspaceMembershipAccessChecker) {

    private val logger = LoggerFactory.getLogger(McpWorkspaceMembershipChecker::class.java)

    @Suppress("TooGenericExceptionCaught")
    fun checkMembership(workspaceId: String, principalId: String): Mono<Boolean> = mono {
        val resourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
        try {
            accessChecker.isActiveMember(principalId, resourceContext)
        } catch (ex: RuntimeException) {
            logger.warn(
                "mcp.workspace-membership-check-failed workspaceId={} principalId={}",
                workspaceId,
                principalId,
                ex,
            )
            false
        }
    }
}
