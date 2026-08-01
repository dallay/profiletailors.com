package com.profiletailors.smp.mcp.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class McpToolInvocationAuditFactTest {

    @Test
    fun `creates audit fact with all fields`() {
        val fact = McpToolInvocationAuditFact(
            toolName = "list_channels",
            scopeChecked = "mcp:channels:read",
            grantedScopes = setOf("mcp:channels:read", "mcp:publications:read"),
            workspaceId = "ws-123",
            correlationId = "corr-456",
            outcome = McpToolInvocationOutcome.SUCCESS,
        )

        assertThat(fact.toolName).isEqualTo("list_channels")
        assertThat(fact.scopeChecked).isEqualTo("mcp:channels:read")
        assertThat(fact.grantedScopes).containsExactlyInAnyOrder("mcp:channels:read", "mcp:publications:read")
        assertThat(fact.workspaceId).isEqualTo("ws-123")
        assertThat(fact.correlationId).isEqualTo("corr-456")
        assertThat(fact.outcome).isEqualTo(McpToolInvocationOutcome.SUCCESS)
    }

    @Test
    fun `outcome enum has expected values`() {
        assertThat(McpToolInvocationOutcome.entries).containsExactlyInAnyOrder(
            McpToolInvocationOutcome.SUCCESS,
            McpToolInvocationOutcome.DENIED,
            McpToolInvocationOutcome.RATE_LIMITED,
            McpToolInvocationOutcome.ERROR,
        )
    }

    @Test
    fun `toMap produces serializable representation`() {
        val fact = McpToolInvocationAuditFact(
            toolName = "get_calendar",
            scopeChecked = "mcp:publications:read",
            grantedScopes = setOf("mcp:publications:read"),
            workspaceId = "ws-789",
            correlationId = "corr-000",
            outcome = McpToolInvocationOutcome.DENIED,
        )

        val map = fact.toMap()
        assertThat(map["toolName"]).isEqualTo("get_calendar")
        assertThat(map["outcome"]).isEqualTo("DENIED")
        assertThat(map).containsKeys(
            "toolName",
            "scopeChecked",
            "grantedScopes",
            "workspaceId",
            "correlationId",
            "outcome",
        )
    }
}
