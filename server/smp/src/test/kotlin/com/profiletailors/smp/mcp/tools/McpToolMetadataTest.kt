package com.profiletailors.smp.mcp.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class McpToolMetadataTest {

    @Test
    fun `list_channels requires mcp channels read scope`() {
        assertThat(McpToolMetadata.requiredScope("list_channels")).isEqualTo("mcp:channels:read")
    }

    @Test
    fun `list_publications requires mcp publications read scope`() {
        assertThat(McpToolMetadata.requiredScope("list_publications")).isEqualTo("mcp:publications:read")
    }

    @Test
    fun `get_calendar requires mcp publications read scope`() {
        assertThat(McpToolMetadata.requiredScope("get_calendar")).isEqualTo("mcp:publications:read")
    }

    @Test
    fun `list_providers requires mcp publications read scope`() {
        assertThat(McpToolMetadata.requiredScope("list_providers")).isEqualTo("mcp:publications:read")
    }

    @Test
    fun `unknown tool returns null`() {
        assertThat(McpToolMetadata.requiredScope("unknown_tool")).isNull()
    }

    @Test
    fun `allTools returns all registered tool names`() {
        assertThat(McpToolMetadata.allTools()).containsExactlyInAnyOrder(
            "list_channels",
            "list_publications",
            "get_calendar",
            "list_providers",
        )
    }

    @Test
    fun `rateLimitBucket returns correct bucket for each tool`() {
        assertThat(McpToolMetadata.rateLimitBucket("list_channels")).isEqualTo("mcp-channels-read")
        assertThat(McpToolMetadata.rateLimitBucket("list_publications")).isEqualTo("mcp-publications-read")
        assertThat(McpToolMetadata.rateLimitBucket("get_calendar")).isEqualTo("mcp-publications-read")
        assertThat(McpToolMetadata.rateLimitBucket("list_providers")).isEqualTo("mcp-publications-read")
    }
}
