package com.profiletailors.smp.mcp.infrastructure.security

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.mcp.infrastructure.ApplicationError
import com.profiletailors.smp.mcp.infrastructure.McpInsufficientScopeException
import com.profiletailors.smp.mcp.tools.McpToolMetadata

@Service
class McpToolInvocationAuthorizer {

    fun authorize(toolName: String, scopes: Set<String>): Boolean {
        if (!McpToolMetadata.isRegistered(toolName)) return false
        val required = McpToolMetadata.requiredScope(toolName) ?: return true
        return scopes.contains(required)
    }

    fun authorizeOrError(toolName: String, scopes: Set<String>): ApplicationError? {
        if (!McpToolMetadata.isRegistered(toolName)) {
            return ApplicationError(
                code = "insufficient_scope",
                category = "authorization",
                message = "Tool '$toolName' is not registered.",
                retryable = false,
                correlationId = java.util.UUID.randomUUID().toString(),
            )
        }
        if (authorize(toolName, scopes)) return null
        val required = McpToolMetadata.requiredScope(toolName) ?: ""
        return ApplicationError(
            code = "insufficient_scope",
            category = "authorization",
            message = "Token does not carry the required scope '$required'.",
            retryable = false,
            correlationId = java.util.UUID.randomUUID().toString(),
        )
    }

    fun requireAuthorized(toolName: String, scopes: Set<String>) {
        if (authorize(toolName, scopes)) return
        throw McpInsufficientScopeException(McpToolMetadata.requiredScope(toolName) ?: toolName)
    }
}
