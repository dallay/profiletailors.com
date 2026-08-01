package com.profiletailors.smp.mcp.infrastructure.security

import com.profiletailors.common.domain.Service
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

/**
 * Resolves the current workspace context from the security context.
 *
 * Extracts the workspace_id from the authenticated McpAuthenticationToken.
 */
@Service
class McpWorkspaceContextResolver {

    /**
     * Retrieves the workspace ID from the current security context.
     *
     * @return Mono emitting the workspace ID, or empty if not authenticated
     */
    fun resolveWorkspaceId(): Mono<String> = ReactiveSecurityContextHolder.getContext()
        .mapNotNull { it.authentication }
        .filter { it is McpAuthenticationToken }
        .cast(McpAuthenticationToken::class.java)
        .map { it.workspaceId }
}
