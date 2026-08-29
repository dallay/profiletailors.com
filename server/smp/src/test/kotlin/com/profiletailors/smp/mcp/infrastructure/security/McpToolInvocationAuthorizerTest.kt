package com.profiletailors.smp.mcp.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class McpToolInvocationAuthorizerTest {

    private val authorizer = McpToolInvocationAuthorizer()

    @Test
    fun `mcp_ping is the only tool with no required scope`() {
        assertThat(authorizer.authorize("mcp_ping", emptySet())).isTrue()
        assertThat(authorizer.authorize("mcp_ping", setOf("unrelated:scope"))).isTrue()
        assertThat(authorizer.authorizeOrError("mcp_ping", emptySet())).isNull()
    }

    @Test
    fun `list_channels requires mcp channels read scope`() {
        assertThat(authorizer.authorize("list_channels", setOf("mcp:channels:read"))).isTrue()
        assertThat(authorizer.authorize("list_channels", setOf("mcp:publications:read"))).isFalse()
        assertThat(authorizer.authorize("list_channels", emptySet())).isFalse()
    }

    @Test
    fun `list_publications requires mcp publications read scope`() {
        assertThat(authorizer.authorize("list_publications", setOf("mcp:publications:read"))).isTrue()
        assertThat(authorizer.authorize("list_publications", setOf("mcp:channels:read"))).isFalse()
        assertThat(authorizer.authorize("list_publications", emptySet())).isFalse()
    }

    @Test
    fun `get_calendar requires mcp publications read scope`() {
        assertThat(authorizer.authorize("get_calendar", setOf("mcp:publications:read"))).isTrue()
        assertThat(authorizer.authorize("get_calendar", setOf("mcp:channels:read"))).isFalse()
    }

    @Test
    fun `list_providers requires mcp providers read scope`() {
        assertThat(authorizer.authorize("list_providers", setOf("mcp:providers:read"))).isTrue()
        assertThat(authorizer.authorize("list_providers", setOf("mcp:channels:read"))).isFalse()
    }

    @Test
    fun `unknown tool name fails closed`() {
        assertThat(authorizer.authorize("not_a_real_tool", setOf("mcp:channels:read"))).isFalse()
    }

    @Test
    fun `token with unrelated scope cannot invoke a known tool`() {
        assertThat(authorizer.authorize("list_channels", setOf("mcp:publications:read"))).isFalse()
        assertThat(authorizer.authorize("list_providers", setOf("mcp:channels:read"))).isFalse()
        assertThat(authorizer.authorize("get_calendar", setOf("mcp:channels:read"))).isFalse()
    }

    @Test
    fun `authorizer does not grant access based on prefix matching`() {
        assertThat(authorizer.authorize("list_channels", setOf("mcp:channels:write"))).isFalse()
        assertThat(authorizer.authorize("list_channels", setOf("mcp:channels"))).isFalse()
    }

    @Test
    fun `missing required scope produces insufficient_scope application error`() {
        val result = authorizer.authorizeOrError("list_channels", setOf("mcp:publications:read"))
        assertThat(result).isNotNull
        assertThat(result!!.code).isEqualTo("insufficient_scope")
        assertThat(result.category).isEqualTo("authorization")
        assertThat(result.retryable).isFalse()
        assertThat(result.message).contains("mcp:channels:read")
    }

    @Test
    fun `granted required scope produces null application error`() {
        val result = authorizer.authorizeOrError("list_channels", setOf("mcp:channels:read"))
        assertThat(result).isNull()
    }

    @Test
    fun `ping access bypasses authorization check`() {
        val result = authorizer.authorizeOrError("mcp_ping", emptySet())
        assertThat(result).isNull()
    }
}
