package com.profiletailors.smp.mcp.tools

/**
 * Static mapping from MCP tool names to required JWT scopes and rate-limit buckets.
 *
 * A `null` [requiredScope] means the tool is callable without any MCP scope (for
 * example `mcp_ping`, which only checks authentication + workspace membership).
 */
object McpToolMetadata {

    private data class ToolEntry(val scope: String?, val rateLimitBucket: String?)

    private const val writeScope = "mcp:publications:write"
    private const val writeBucket = "mcp-publications-write"
    private const val readPublicationsScope = "mcp:publications:read"
    private const val readPublicationsBucket = "mcp-publications-read"
    private const val channelsScope = "mcp:channels:read"
    private const val channelsBucket = "mcp-channels-read"
    private const val providersScope = "mcp:providers:read"

    private val registry: Map<String, ToolEntry> = mapOf(
        "mcp_ping" to ToolEntry(scope = null, rateLimitBucket = null),
        "list_channels" to ToolEntry(scope = channelsScope, rateLimitBucket = channelsBucket),
        "list_publications" to ToolEntry(scope = readPublicationsScope, rateLimitBucket = readPublicationsBucket),
        "get_calendar" to ToolEntry(scope = readPublicationsScope, rateLimitBucket = readPublicationsBucket),
        "list_providers" to ToolEntry(scope = providersScope, rateLimitBucket = readPublicationsBucket),
        "create_publication" to ToolEntry(scope = writeScope, rateLimitBucket = writeBucket),
        "edit_publication" to ToolEntry(scope = writeScope, rateLimitBucket = writeBucket),
        "delete_publication" to ToolEntry(scope = writeScope, rateLimitBucket = writeBucket),
        "cancel_publication" to ToolEntry(scope = writeScope, rateLimitBucket = writeBucket),
        "retry_publication" to ToolEntry(scope = writeScope, rateLimitBucket = writeBucket),
    )

    fun requiredScope(toolName: String): String? = registry[toolName]?.scope

    fun rateLimitBucket(toolName: String): String? = registry[toolName]?.rateLimitBucket

    fun allTools(): Set<String> = registry.keys

    fun isRegistered(toolName: String): Boolean = registry.containsKey(toolName)
}
