package com.profiletailors.smp.mcp.adapter

/**
 * Static mapping from MCP tool names to required JWT scopes and rate-limit buckets.
 */
object McpToolMetadata {

    private data class ToolEntry(val scope: String, val rateLimitBucket: String)

    private val registry: Map<String, ToolEntry> = mapOf(
        "list_channels" to ToolEntry("mcp:channels:read", "mcp-channels-read"),
        "list_publications" to ToolEntry("mcp:publications:read", "mcp-publications-read"),
        "get_calendar" to ToolEntry("mcp:publications:read", "mcp-publications-read"),
        "list_providers" to ToolEntry("mcp:publications:read", "mcp-publications-read"),
    )

    fun requiredScope(toolName: String): String? = registry[toolName]?.scope

    fun rateLimitBucket(toolName: String): String? = registry[toolName]?.rateLimitBucket

    fun allTools(): Set<String> = registry.keys
}
