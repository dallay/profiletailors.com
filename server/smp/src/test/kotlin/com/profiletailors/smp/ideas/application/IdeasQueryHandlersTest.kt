package com.profiletailors.smp.ideas.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.ideas.domain.Idea
import com.profiletailors.smp.ideas.domain.IdeaBoardConfig
import com.profiletailors.smp.ideas.domain.IdeaBoardConfigRepository
import com.profiletailors.smp.ideas.domain.IdeaColumn
import com.profiletailors.smp.ideas.domain.IdeaRepository
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class IdeasQueryHandlersTest {
    private val workspaceId = "workspace-1"
    private val now = Instant.parse("2026-08-01T12:00:00Z")

    @Test
    fun `list handler scopes repository access and normalizes order within columns`() = runTest {
        val repository = FakeIdeaRepository(
            listOf(
                idea("idea-2", columnId = "raw", order = 4),
                idea("idea-other", columnId = "done", order = 0),
                idea("idea-1", columnId = "raw", order = 1),
            ),
        )

        val result = ListIdeasHandler(FixedResourceContextProvider(workspaceId), repository)
            .handle(ListIdeasQuery)

        assertEquals(listOf("workspace-1"), repository.listedWorkspaces)
        assertEquals(listOf(0, 1), result.ideas.filter { it.columnId == "raw" }.map { it.orderInColumn })
        assertEquals(listOf("idea-1", "idea-2"), result.ideas.filter { it.columnId == "raw" }.map { it.id })
    }

    @Test
    fun `get handler returns an owned idea and rejects a missing idea`() = runTest {
        val repository = FakeIdeaRepository(listOf(idea("idea-1", columnId = "raw", order = 0)))
        val handler = GetIdeaHandler(FixedResourceContextProvider(workspaceId), repository)

        assertEquals("idea-1", handler.handle(GetIdeaQuery("idea-1")).id)
        assertThrows(IdeaNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(GetIdeaQuery("missing")) }
        }
        assertEquals(listOf("workspace-1", "workspace-1"), repository.lookedUpWorkspaces)
    }

    @Test
    fun `columns handler normalizes configured order and falls back to defaults`() = runTest {
        val configured = FakeBoardRepository(
            IdeaBoardConfig(
                workspaceId,
                listOf(
                    IdeaColumn("done", "Done", order = 8),
                    IdeaColumn("raw", "Raw", order = 2),
                ),
            ),
        )
        val handler = GetColumnsHandler(FixedResourceContextProvider(workspaceId), configured)

        assertEquals(listOf("raw", "done"), handler.handle(GetColumnsQuery).columns.map { it.id })

        val defaults = GetColumnsHandler(FixedResourceContextProvider(workspaceId), FakeBoardRepository())
        assertEquals(
            listOf("raw", "in-progress", "done"),
            defaults.handle(GetColumnsQuery).columns.map { it.id },
        )
    }

    @Test
    fun `query handlers reject a context without a workspace`() {
        val context = FixedResourceContextProvider(null)

        assertThrows(WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                ListIdeasHandler(context, FakeIdeaRepository(emptyList())).handle(ListIdeasQuery)
            }
        }
        assertThrows(WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                GetColumnsHandler(context, FakeBoardRepository()).handle(GetColumnsQuery)
            }
        }
    }

    private fun idea(id: String, columnId: String, order: Int) = Idea(
        id = id,
        workspaceId = workspaceId,
        title = id,
        columnId = columnId,
        orderInColumn = order,
        createdAt = now,
        updatedAt = now,
    )

    private class FixedResourceContextProvider(private val workspaceId: String?) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }

    private class FakeIdeaRepository(private val ideas: List<Idea>) : IdeaRepository {
        val listedWorkspaces = mutableListOf<String>()
        val lookedUpWorkspaces = mutableListOf<String>()

        override suspend fun listByWorkspace(workspaceId: String): List<Idea> {
            listedWorkspaces += workspaceId
            return ideas.filter { it.workspaceId == workspaceId }
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, ideaId: String): Idea? {
            lookedUpWorkspaces += workspaceId
            return ideas.firstOrNull { it.workspaceId == workspaceId && it.id == ideaId }
        }

        override suspend fun create(idea: Idea): Idea = idea

        override suspend fun update(idea: Idea): Idea = idea

        override suspend fun delete(workspaceId: String, ideaId: String): Boolean = false
    }

    private class FakeBoardRepository(private val config: IdeaBoardConfig? = null) : IdeaBoardConfigRepository {
        override suspend fun findByWorkspace(workspaceId: String): IdeaBoardConfig? =
            config?.takeIf { it.workspaceId == workspaceId }

        override suspend fun upsert(config: IdeaBoardConfig): IdeaBoardConfig = config
    }
}
