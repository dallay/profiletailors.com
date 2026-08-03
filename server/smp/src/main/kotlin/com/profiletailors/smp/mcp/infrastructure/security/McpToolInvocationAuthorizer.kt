package com.profiletailors.smp.mcp.infrastructure.security

import com.profiletailors.common.domain.Service

/**
 * Authorizes tool invocation based on JWT scopes.
 *
 * Checks if the authenticated token contains the required scope
 * for the requested tool.
 */
@Service
class McpToolInvocationAuthorizer {

    /**
     * Checks if the provided scopes allow invocation of the specified tool.
     *
     * @param toolName The name of the tool being invoked
     * @param scopes The set of scopes from the JWT token
     * @return true if authorized, false otherwise
     */
    @Suppress("UnusedParameter")
    fun authorize(toolName: String, scopes: Set<String>): Boolean = // For now, any valid MCP scope authorizes any tool
        // Future refinement: map specific tools to required scopes
        scopes.any { it.startsWith("mcp:") }
}
