package com.profiletailors.smp.mcp.tools

/**
 * Static mapping from MCP tool names to required JWT scopes and rate-limit buckets.
 *
 * A `null` [requiredScope] means the tool is callable without any MCP scope (for
 * example `mcp_ping`, which only checks authentication + workspace membership).
 */
object McpToolMetadata {

    private data class ToolEntry(val scope: String?, val rateLimitBucket: String?)

    private const val WRITE_SCOPE = "mcp:publications:write"
    private const val WRITE_BUCKET = "mcp-publications-write"
    private const val READ_PUBLICATIONS_SCOPE = "mcp:publications:read"
    private const val READ_PUBLICATIONS_BUCKET = "mcp-publications-read"
    private const val CHANNELS_SCOPE = "mcp:channels:read"
    private const val CHANNELS_BUCKET = "mcp-channels-read"
    private const val PROVIDERS_SCOPE = "mcp:providers:read"

    private val registry: Map<String, ToolEntry> = mapOf(
        "mcp_ping" to ToolEntry(scope = null, rateLimitBucket = null),
        "list_channels" to ToolEntry(scope = CHANNELS_SCOPE, rateLimitBucket = CHANNELS_BUCKET),
        "list_publications" to ToolEntry(scope = READ_PUBLICATIONS_SCOPE, rateLimitBucket = READ_PUBLICATIONS_BUCKET),
        "get_calendar" to ToolEntry(scope = READ_PUBLICATIONS_SCOPE, rateLimitBucket = READ_PUBLICATIONS_BUCKET),
        "list_providers" to ToolEntry(scope = PROVIDERS_SCOPE, rateLimitBucket = READ_PUBLICATIONS_BUCKET),
        "create_publication" to ToolEntry(scope = WRITE_SCOPE, rateLimitBucket = WRITE_BUCKET),
        "edit_publication" to ToolEntry(scope = WRITE_SCOPE, rateLimitBucket = WRITE_BUCKET),
        "delete_publication" to ToolEntry(scope = WRITE_SCOPE, rateLimitBucket = WRITE_BUCKET),
        "cancel_publication" to ToolEntry(scope = WRITE_SCOPE, rateLimitBucket = WRITE_BUCKET),
        "retry_publication" to ToolEntry(scope = WRITE_SCOPE, rateLimitBucket = WRITE_BUCKET),
    )

    fun requiredScope(toolName: String): String? = registry[toolName]?.scope

    fun rateLimitBucket(toolName: String): String? = registry[toolName]?.rateLimitBucket

    fun allTools(): Set<String> = registry.keys

    fun isRegistered(toolName: String): Boolean = registry.containsKey(toolName)
}
