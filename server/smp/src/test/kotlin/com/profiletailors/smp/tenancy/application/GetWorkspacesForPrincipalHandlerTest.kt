package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetWorkspacesForPrincipalHandlerTest {

    private val principalContext = PrincipalContext(
        principalId = "user-1",
        principalType = PrincipalType.USER,
        subject = "subject-user-1",
    )

    private val principalContextProvider = object : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class InMemoryWorkspaceReadRepository(private val data: Map<String, List<WorkspaceSummary>> = emptyMap()) :
        WorkspaceReadRepository {
        override suspend fun findWorkspacesByPrincipal(principalId: String): List<WorkspaceSummary> =
            data[principalId] ?: emptyList()
    }

    @Test
    fun `returns workspaces for authenticated principal`() = runTest {
        val repository = InMemoryWorkspaceReadRepository(
            data = mapOf(
                "user-1" to listOf(
                    WorkspaceSummary(workspaceId = "ws-1", name = "Workspace 1", role = "OWNER"),
                    WorkspaceSummary(workspaceId = "ws-2", name = "Workspace 2", role = "MEMBER"),
                ),
            ),
        )
        val handler = GetWorkspacesForPrincipalHandler(principalContextProvider, repository)

        val result = handler.handle(GetWorkspacesForPrincipalQuery)

        assertEquals(2, result.size)
        assertEquals("ws-1", result[0].workspaceId)
        assertEquals("ws-2", result[1].workspaceId)
    }

    @Test
    fun `returns empty list when principal has no workspaces`() = runTest {
        val repository = InMemoryWorkspaceReadRepository()
        val handler = GetWorkspacesForPrincipalHandler(principalContextProvider, repository)

        val result = handler.handle(GetWorkspacesForPrincipalQuery)

        assertEquals(0, result.size)
    }
}
