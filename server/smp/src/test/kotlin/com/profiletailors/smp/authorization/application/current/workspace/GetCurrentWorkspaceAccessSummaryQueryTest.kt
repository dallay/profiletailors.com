package com.profiletailors.smp.authorization.application.current.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GetCurrentWorkspaceAccessSummaryQueryTest {

    @Test
    fun `should be a singleton object`() {
        assertThat(GetCurrentWorkspaceAccessSummaryQuery).isNotNull
    }

    @Test
    fun `should define access entitlement constant`() {
        assertThat(GetCurrentWorkspaceAccessSummaryQuery.CURRENT_WORKSPACE_ACCESS_ENTITLEMENT)
            .isEqualTo("workspace.access.summary")
    }

    @Test
    fun `workspace access summary should hold values`() {
        val summary = WorkspaceAccessSummary(
            workspaceId = "ws-1",
            principalId = "user-1",
            roles = listOf("admin"),
            permissions = listOf("read", "write"),
        )

        assertThat(summary.workspaceId).isEqualTo("ws-1")
        assertThat(summary.principalId).isEqualTo("user-1")
        assertThat(summary.roles).containsExactly("admin")
        assertThat(summary.permissions).containsExactly("read", "write")
    }
}
