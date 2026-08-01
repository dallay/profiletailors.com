package com.profiletailors.smp.mcp.infrastructure

/**
 * Outcome of an MCP tool invocation attempt.
 */
enum class McpToolInvocationOutcome {
    SUCCESS,
    DENIED,
    RATE_LIMITED,
    ERROR,
}

/**
 * Audit fact emitted on every MCP tool invocation.
 *
 * Captures tool name, scope checked, granted scopes, workspace, correlation,
 * and outcome for observability and compliance.
 */
data class McpToolInvocationAuditFact(
    val toolName: String,
    val scopeChecked: String,
    val grantedScopes: Set<String>,
    val workspaceId: String,
    val correlationId: String,
    val outcome: McpToolInvocationOutcome,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "toolName" to toolName,
        "scopeChecked" to scopeChecked,
        "grantedScopes" to grantedScopes.joinToString(","),
        "workspaceId" to workspaceId,
        "correlationId" to correlationId,
        "outcome" to outcome.name,
    )
}
