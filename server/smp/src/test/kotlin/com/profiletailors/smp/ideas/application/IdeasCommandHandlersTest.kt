package com.profiletailors.smp.ideas.application

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.ideas.domain.Idea
import com.profiletailors.smp.ideas.domain.IdeaBoardConfig
import com.profiletailors.smp.ideas.domain.IdeaBoardConfigRepository
import com.profiletailors.smp.ideas.domain.IdeaColumn
import com.profiletailors.smp.ideas.domain.IdeaLink
import com.profiletailors.smp.ideas.domain.IdeaRepository
import com.profiletailors.smp.publishing.application.ConnectedChannelsResponse
import com.profiletailors.smp.publishing.application.ConnectedSocialChannelSummary
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipOperationRequiresWorkspaceContextException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class IdeasCommandHandlersTest {
    private val workspaceId = "workspace-1"
    private val now = Instant.parse("2026-08-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `create handler provisions default board and normalizes title`() = runTest {
        val ideas = FakeIdeaRepository()
        val boards = FakeBoardRepository()
        val handler = CreateIdeaHandler(FixedResourceContextProvider(workspaceId), ideas, boards, clock)

        val result = handler.handle(
            CreateIdeaCommand(
                title = "  First idea  ",
                tags = listOf("testing"),
                links = listOf(IdeaLink("https://example.com", "Docs")),
            ),
        )

        assertEquals("First idea", result.title)
        assertEquals("raw", result.columnId)
        assertEquals(0, result.orderInColumn)
        assertEquals(1, boards.upserted.size)
        assertEquals(1, ideas.created.size)
    }

    @Test
    fun `create handler uses requested column and appends after existing ideas`() = runTest {
        val existing = idea("idea-existing", columnId = "done", order = 0)
        val ideas = FakeIdeaRepository(mutableListOf(existing))
        val boards = FakeBoardRepository(
            IdeaBoardConfig(
                workspaceId,
                listOf(IdeaColumn("done", "Done", order = 2), IdeaColumn("raw", "Raw", order = 1)),
            ),
        )
        val handler = CreateIdeaHandler(FixedResourceContextProvider(workspaceId), ideas, boards, clock)

        val result = handler.handle(CreateIdeaCommand(title = "Second", columnId = "done"))

        assertEquals("done", result.columnId)
        assertEquals(1, result.orderInColumn)
        assertEquals(0, boards.upserted.size)
    }

    @Test
    fun `create handler falls back to default column when configured board is empty`() = runTest {
        val ideas = FakeIdeaRepository()
        val boards = FakeBoardRepository(IdeaBoardConfig(workspaceId, emptyList()))
        val handler = CreateIdeaHandler(FixedResourceContextProvider(workspaceId), ideas, boards, clock)

        val result = handler.handle(CreateIdeaCommand(title = "Fallback"))

        assertEquals("raw", result.columnId)
    }

    @Test
    fun `move handler clamps negative order and normalizes the workspace`() = runTest {
        val existing = idea("idea-1", columnId = "raw", order = 1)
        val ideas = FakeIdeaRepository(mutableListOf(existing, idea("idea-2", "raw", 0)))
        val handler = MoveIdeaHandler(FixedResourceContextProvider(workspaceId), ideas, clock)

        val result = handler.handle(MoveIdeaCommand("idea-1", "done", -4))

        assertEquals("done", result.columnId)
        assertEquals(0, result.orderInColumn)
        assertEquals(0, ideas.updated.first { it.id == "idea-1" }.orderInColumn)
    }

    @Test
    fun `update handler reports a missing idea`() = runTest {
        val handler = UpdateIdeaHandler(
            FixedResourceContextProvider(workspaceId),
            FakeIdeaRepository(),
            clock,
        )

        assertThrows(IdeaNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(UpdateIdeaCommand("missing", title = "Updated"))
            }
        }
    }

    @Test
    fun `update handler preserves omitted fields and trims supplied title`() = runTest {
        val existing = idea("idea-1", columnId = "raw", order = 0).copy(
            notes = "Existing notes",
            tags = listOf("old"),
            links = listOf(IdeaLink("https://old.example")),
        )
        val ideas = FakeIdeaRepository(mutableListOf(existing))
        val handler = UpdateIdeaHandler(FixedResourceContextProvider(workspaceId), ideas, clock)

        val result = handler.handle(UpdateIdeaCommand("idea-1", title = "  Updated  "))

        assertEquals("Updated", result.title)
        assertEquals("Existing notes", result.notes)
        assertEquals(listOf("old"), result.tags)
        assertEquals(listOf(IdeaLink("https://old.example")), result.links)
    }

    @Test
    fun `delete handler returns deleted idea and rejects a failed delete`() = runTest {
        val existing = idea("idea-1", columnId = "raw", order = 0)
        val ideas = FakeIdeaRepository(mutableListOf(existing))
        val handler = DeleteIdeaHandler(FixedResourceContextProvider(workspaceId), ideas)

        assertEquals("idea-1", handler.handle(DeleteIdeaCommand("idea-1")).id)

        val failedDelete = FakeIdeaRepository(mutableListOf(existing), deleteResult = false)
        val failingHandler = DeleteIdeaHandler(FixedResourceContextProvider(workspaceId), failedDelete)
        assertThrows(IdeaNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking { failingHandler.handle(DeleteIdeaCommand("idea-1")) }
        }
    }

    @Test
    fun `query handlers normalize list and use configured or default columns`() = runTest {
        val ideas = FakeIdeaRepository(mutableListOf(idea("idea-1", "raw", 3), idea("idea-2", "raw", 1)))
        val list = ListIdeasHandler(FixedResourceContextProvider(workspaceId), ideas)
        assertEquals(listOf(0, 1), list.handle(ListIdeasQuery).ideas.map { it.orderInColumn })

        val emptyBoards = FakeBoardRepository()
        val defaultColumns = GetColumnsHandler(FixedResourceContextProvider(workspaceId), emptyBoards)
        assertEquals(listOf("raw", "in-progress", "done"), defaultColumns.handle(GetColumnsQuery).columns.map { it.id })

        val configured =
            FakeBoardRepository(IdeaBoardConfig(workspaceId, listOf(IdeaColumn("done", "Done", order = 3))))
        val configuredColumns = GetColumnsHandler(FixedResourceContextProvider(workspaceId), configured)
        assertEquals(listOf("done"), configuredColumns.handle(GetColumnsQuery).columns.map { it.id })
    }

    @Test
    fun `get idea handler returns the workspace-owned idea`() = runTest {
        val ideas = FakeIdeaRepository(mutableListOf(idea("idea-1", "raw", 0)))

        val result = GetIdeaHandler(FixedResourceContextProvider(workspaceId), ideas)
            .handle(GetIdeaQuery("idea-1"))

        assertEquals("idea-1", result.id)
        assertEquals(workspaceId, result.workspaceId)
    }

    @Test
    fun `delete handler rejects a missing idea before deleting`() = runTest {
        val handler = DeleteIdeaHandler(FixedResourceContextProvider(workspaceId), FakeIdeaRepository())

        assertThrows(IdeaNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(DeleteIdeaCommand("missing")) }
        }
    }

    @Test
    fun `convert handler supports ideas without optional notes or tags`() = runTest {
        val ideas = FakeIdeaRepository(mutableListOf(idea("idea-plain", "raw", 0)))
        val mediator = CapturingMediator(
            ConnectedChannelsResponse(
                listOf(
                    ConnectedSocialChannelSummary(
                        socialAccountId = "account-1",
                        connectionId = "connection-1",
                        provider = SocialProvider.LINKEDIN,
                        accountKind = SocialAccountKind.PERSONAL_PROFILE,
                        displayName = "Author",
                        status = SocialConnectionStatus.ACTIVE,
                        connectedAt = now,
                        lastSyncedAt = now,
                    ),
                ),
            ),
        )

        ConvertIdeaHandler(FixedResourceContextProvider(workspaceId), ideas, mediator, clock)
            .handle(ConvertIdeaCommand("idea-plain"))

        assertEquals("idea-plain", mediator.publicationCommand?.bodyText)
    }

    @Test
    fun `convert handler builds body updates idea and requires an active channel`() = runTest {
        val existing = idea("idea-1", "raw", 0).copy(notes = "Notes", tags = listOf("launch", "testing"))
        val ideas = FakeIdeaRepository(mutableListOf(existing))
        val mediator = CapturingMediator(
            channels = ConnectedChannelsResponse(
                listOf(
                    ConnectedSocialChannelSummary(
                        socialAccountId = "account-1",
                        connectionId = "connection-1",
                        provider = SocialProvider.LINKEDIN,
                        accountKind = SocialAccountKind.PERSONAL_PROFILE,
                        displayName = "Author",
                        status = SocialConnectionStatus.ACTIVE,
                        connectedAt = now,
                        lastSyncedAt = now,
                    ),
                ),
            ),
        )
        val handler = ConvertIdeaHandler(FixedResourceContextProvider(workspaceId), ideas, mediator, clock)

        val result = handler.handle(ConvertIdeaCommand("idea-1"))

        assertEquals("pub-1", result.publicationId)
        assertEquals("pub-1", ideas.updated.last().convertedToPublicationId)
        assertEquals(
            """
            idea-1

            Notes

            #launch #testing
            """.trimIndent(),
            mediator.publicationCommand?.bodyText,
        )

        val noChannels = ConvertIdeaHandler(
            FixedResourceContextProvider(workspaceId),
            FakeIdeaRepository(mutableListOf(existing)),
            CapturingMediator(ConnectedChannelsResponse(emptyList())),
            clock,
        )
        assertThrows(InvalidIdeaColumnsException::class.java) {
            kotlinx.coroutines.runBlocking { noChannels.handle(ConvertIdeaCommand("idea-1")) }
        }
    }

    @Test
    fun `update columns rejects empty input and moves ideas from removed columns`() = runTest {
        val ideas = FakeIdeaRepository(mutableListOf(idea("idea-1", "removed", 4)))
        val boards = FakeBoardRepository()
        val handler = UpdateColumnsHandler(FixedResourceContextProvider(workspaceId), boards, ideas)

        assertThrows(InvalidIdeaColumnsException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(UpdateColumnsCommand(emptyList())) }
        }
        val response = handler.handle(UpdateColumnsCommand(listOf(IdeaColumn("done", "Done", order = 4))))
        assertEquals("done", response.columns.single().id)
        assertEquals("done", ideas.updated.last().columnId)
    }

    @Test
    fun `handlers reject requests without a workspace context`() = runTest {
        val noWorkspace = FixedResourceContextProvider(null)

        assertThrows(WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                GetIdeaHandler(noWorkspace, FakeIdeaRepository()).handle(GetIdeaQuery("idea-1"))
            }
        }
        assertThrows(WorkspaceOwnershipOperationRequiresWorkspaceContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                ListIdeasHandler(noWorkspace, FakeIdeaRepository()).handle(ListIdeasQuery)
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

    private class FakeIdeaRepository(
        initial: MutableList<Idea> = mutableListOf(),
        private val deleteResult: Boolean? = null,
    ) : IdeaRepository {
        private val state = initial
        val created = mutableListOf<Idea>()
        val updated = mutableListOf<Idea>()

        override suspend fun listByWorkspace(workspaceId: String): List<Idea> = state.filter {
            it.workspaceId ==
                workspaceId
        }

        override suspend fun findByWorkspaceAndId(workspaceId: String, ideaId: String): Idea? =
            state.firstOrNull { it.workspaceId == workspaceId && it.id == ideaId }

        override suspend fun create(idea: Idea): Idea {
            state += idea
            created += idea
            return idea
        }

        override suspend fun update(idea: Idea): Idea {
            val index = state.indexOfFirst { it.id == idea.id }
            if (index >= 0) state[index] = idea else state += idea
            updated += idea
            return idea
        }

        override suspend fun delete(workspaceId: String, ideaId: String): Boolean = deleteResult ?: state.removeIf {
            it.workspaceId == workspaceId && it.id == ideaId
        }
    }

    private class FakeBoardRepository(private val existing: IdeaBoardConfig? = null) : IdeaBoardConfigRepository {
        val upserted = mutableListOf<IdeaBoardConfig>()

        override suspend fun findByWorkspace(workspaceId: String): IdeaBoardConfig? = existing

        override suspend fun upsert(config: IdeaBoardConfig): IdeaBoardConfig {
            upserted += config
            return config
        }
    }

    private class CapturingMediator(private val channels: ConnectedChannelsResponse) : Mediator {
        var publicationCommand: CreatePublicationCommand? = null

        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            @Suppress("UNCHECKED_CAST")
            return when (query) {
                is ListConnectedChannelsQuery -> channels as TResponse
                else -> error("Unsupported query")
            }
        }

        override suspend fun <TCommand : Command> send(command: TCommand) = error("Unsupported command")

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            publicationCommand = command as CreatePublicationCommand
            return PublicationResult(
                publicationId = "pub-1",
                workspaceId = "workspace-1",
                socialAccountId = "account-1",
                status = com.profiletailors.smp.publishing.domain.PublicationStatus.QUEUED,
                scheduleMode = com.profiletailors.smp.publishing.domain.ScheduleMode.NOW,
                priority = false,
                title = publicationCommand?.title,
                bodyText = publicationCommand?.bodyText,
                assetIds = emptyList(),
                scheduledFor = null,
                nextSlotAfter = null,
            ) as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) = error("Unsupported notification")

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) =
            error("Unsupported notification")
    }
}
