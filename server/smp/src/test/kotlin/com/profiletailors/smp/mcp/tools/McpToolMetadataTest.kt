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
    fun `list_providers requires mcp providers read scope`() {
        assertThat(McpToolMetadata.requiredScope("list_providers")).isEqualTo("mcp:providers:read")
    }

    @Test
    fun `mcp_ping requires no scope`() {
        assertThat(McpToolMetadata.requiredScope("mcp_ping")).isNull()
    }

    @Test
    fun `unknown tool returns null`() {
        assertThat(McpToolMetadata.requiredScope("unknown_tool")).isNull()
    }

    @Test
    fun `allTools returns all registered tool names`() {
        assertThat(McpToolMetadata.allTools()).containsExactlyInAnyOrder(
            "mcp_ping",
            "list_channels",
            "list_publications",
            "get_calendar",
            "list_providers",
            "create_publication",
            "edit_publication",
            "delete_publication",
            "cancel_publication",
            "retry_publication",
        )
    }

    @Test
    fun `rateLimitBucket returns correct bucket for each tool`() {
        assertThat(McpToolMetadata.rateLimitBucket("list_channels")).isEqualTo("mcp-channels-read")
        assertThat(McpToolMetadata.rateLimitBucket("list_publications")).isEqualTo("mcp-publications-read")
        assertThat(McpToolMetadata.rateLimitBucket("get_calendar")).isEqualTo("mcp-publications-read")
        assertThat(McpToolMetadata.rateLimitBucket("list_providers")).isEqualTo("mcp-publications-read")
        assertThat(McpToolMetadata.rateLimitBucket("mcp_ping")).isNull()
    }

    @Test
    fun `isRegistered reports the ten declared tools`() {
        assertThat(McpToolMetadata.isRegistered("mcp_ping")).isTrue()
        assertThat(McpToolMetadata.isRegistered("list_publications")).isTrue()
        assertThat(McpToolMetadata.isRegistered("get_calendar")).isTrue()
        assertThat(McpToolMetadata.isRegistered("list_channels")).isTrue()
        assertThat(McpToolMetadata.isRegistered("list_providers")).isTrue()
        assertThat(McpToolMetadata.isRegistered("create_publication")).isTrue()
        assertThat(McpToolMetadata.isRegistered("edit_publication")).isTrue()
        assertThat(McpToolMetadata.isRegistered("delete_publication")).isTrue()
        assertThat(McpToolMetadata.isRegistered("cancel_publication")).isTrue()
        assertThat(McpToolMetadata.isRegistered("retry_publication")).isTrue()
    }
}
