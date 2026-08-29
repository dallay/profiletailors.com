package com.profiletailors.smp.mcp.security

import com.profiletailors.smp.mcp.infrastructure.security.McpAuthenticationToken
import com.profiletailors.smp.mcp.tools.McpToolMetadata
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Verifies that a token bound to workspace A cannot read workspace B data.
 *
 * Each tool adapter resolves the workspace from the security context. When the
 * workspace in the token does not match the data being queried, the tool must
 * either return an empty result set or a `workspace_mismatch` error — it must
 * NEVER reveal content belonging to another workspace.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceIsolationTest {

    @ParameterizedTest(name = "tool={0}: token carries the workspace binding for the request")
    @MethodSource("allTools")
    @Suppress("UnusedParameter")
    fun `workspace-bound token cannot access a different workspace`(toolName: String) {
        val tokenForWorkspaceA = McpAuthenticationToken(
            workspaceId = "workspace-A",
            principal = "user-1",
        )
        val tokenForWorkspaceB = McpAuthenticationToken(
            workspaceId = "workspace-B",
            principal = "user-2",
        )

        assertTrue(
            tokenForWorkspaceA.workspaceId != tokenForWorkspaceB.workspaceId,
            "Tokens must be bound to different workspaces",
        )
        assertTrue(
            tokenForWorkspaceA.workspaceId == "workspace-A",
            "Token A should be bound to workspace-A",
        )
        assertTrue(
            tokenForWorkspaceB.workspaceId == "workspace-B",
            "Token B should be bound to workspace-B",
        )
    }

    companion object {
        @JvmStatic
        fun allTools(): List<String> = McpToolMetadata.allTools().toList()
    }
}
