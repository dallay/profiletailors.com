package com.profiletailors.smp.mcp.infrastructure

enum class McpToolInvocationOutcome {
    SUCCESS,
    DENIED,
    RATE_LIMITED,
    ERROR,
}

data class McpToolInvocationAuditFact(
    val toolName: String,
    val scopeChecked: String,
    val grantedScopes: Set<String>,
    val workspaceId: String,
    val correlationId: String,
    val outcome: McpToolInvocationOutcome,
    val publicationId: String? = null,
    val clientToolCallId: String? = null,
    val timestamp: java.time.Instant = java.time.Instant.now(),
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "toolName" to toolName,
        "scopeChecked" to scopeChecked,
        "grantedScopes" to grantedScopes.joinToString(","),
        "workspaceId" to workspaceId,
        "correlationId" to correlationId,
        "outcome" to outcome.name,
        "publicationId" to publicationId,
        "clientToolCallId" to clientToolCallId,
        "timestamp" to timestamp.toString(),
    )
}
