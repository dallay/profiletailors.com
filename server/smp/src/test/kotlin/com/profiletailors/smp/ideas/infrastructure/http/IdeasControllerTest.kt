package com.profiletailors.smp.ideas.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.ideas.application.ColumnsResponse
import com.profiletailors.smp.ideas.application.ConvertIdeaResult
import com.profiletailors.smp.ideas.application.CreateIdeaCommand
import com.profiletailors.smp.ideas.application.DeleteIdeaCommand
import com.profiletailors.smp.ideas.application.GetColumnsQuery
import com.profiletailors.smp.ideas.application.GetIdeaQuery
import com.profiletailors.smp.ideas.application.IdeaNotFoundException
import com.profiletailors.smp.ideas.application.IdeaResult
import com.profiletailors.smp.ideas.application.InvalidIdeaColumnsException
import com.profiletailors.smp.ideas.application.ListIdeasQuery
import com.profiletailors.smp.ideas.application.ListIdeasResponse
import com.profiletailors.smp.ideas.application.MoveIdeaCommand
import com.profiletailors.smp.ideas.application.UpdateColumnsCommand
import com.profiletailors.smp.ideas.application.UpdateIdeaCommand
import com.profiletailors.smp.ideas.domain.IdeaColumn
import com.profiletailors.smp.ideas.domain.IdeaLink
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant

class IdeasControllerTest {
    @Test
    fun `problem details map idea errors to stable responses`() {
        val handler = IdeasProblemDetailsHandler()

        val notFound = handler.handle(IdeaNotFoundException("idea-1"))
        assertEquals(HttpStatus.NOT_FOUND.value(), notFound.status)
        assertEquals("Idea not found", notFound.title)
        assertEquals("idea-1", notFound.properties?.get("ideaId"))

        val invalid = handler.handle(InvalidIdeaColumnsException("Bad columns"))
        assertEquals(HttpStatus.BAD_REQUEST.value(), invalid.status)
        assertEquals("Invalid idea columns", invalid.title)
        assertEquals("Bad columns", invalid.detail)
    }

    @Test
    fun `list ideas dispatches query`() = runTest {
        val mediator = CapturingMediator()
        val controller = IdeasController(mediator)

        val response = controller.listIdeas()

        assertEquals(ListIdeasQuery, mediator.lastQuery)
        assertEquals(1, response.ideas.size)
    }

    @Test
    fun `create idea dispatches command`() = runTest {
        val mediator = CapturingMediator()
        val controller = IdeasController(mediator)

        controller.createIdea(
            CreateIdeaRequest(
                title = "Idea 1",
                notes = "Notes",
                tags = listOf("launch"),
                links = listOf(IdeaLink("https://docs.example", "doc")),
                columnId = "raw",
            ),
        )

        assertEquals(
            CreateIdeaCommand(
                title = "Idea 1",
                notes = "Notes",
                tags = listOf("launch"),
                links = listOf(IdeaLink("https://docs.example", "doc")),
                columnId = "raw",
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `create idea supplies empty optional collections when omitted`() = runTest {
        val mediator = CapturingMediator()
        val controller = IdeasController(mediator)

        controller.createIdea(CreateIdeaRequest(title = "Idea 2"))

        assertEquals(
            CreateIdeaCommand(title = "Idea 2", tags = emptyList(), links = emptyList()),
            mediator.lastRequest,
        )
    }

    @Test
    fun `controller dispatches get update delete and convert idea commands`() = runTest {
        val mediator = CapturingMediator()
        val controller = IdeasController(mediator)

        controller.getIdea("idea-1")
        assertEquals(GetIdeaQuery("idea-1"), mediator.lastQuery)
        controller.updateIdea("idea-1", UpdateIdeaRequest(title = "Updated"))
        assertEquals(UpdateIdeaCommand("idea-1", title = "Updated"), mediator.lastRequest)
        controller.deleteIdea("idea-1")
        assertEquals(DeleteIdeaCommand("idea-1"), mediator.lastRequest)
        controller.convertIdea("idea-1")
        assertEquals(com.profiletailors.smp.ideas.application.ConvertIdeaCommand("idea-1"), mediator.lastRequest)
    }

    @Test
    fun `move idea dispatches command`() = runTest {
        val mediator = CapturingMediator()
        val controller = IdeasController(mediator)

        controller.moveIdea(
            "idea-1",
            MoveIdeaRequest(
                columnId = "done",
                orderInColumn = 1,
            ),
        )

        assertEquals(MoveIdeaCommand("idea-1", "done", 1), mediator.lastRequest)
    }

    @Test
    fun `columns endpoints dispatch queries and commands`() = runTest {
        val mediator = CapturingMediator()
        val controller = IdeasController(mediator)

        controller.getColumns()
        assertEquals(GetColumnsQuery, mediator.lastQuery)

        val columns = listOf(
            IdeaColumn("raw", "Raw", null, 0),
            IdeaColumn("done", "Done", "#10b981", 1),
        )
        controller.updateColumns(UpdateColumnsRequest(columns))
        assertEquals(UpdateColumnsCommand(columns), mediator.lastRequest)
    }

    private class CapturingMediator : Mediator {
        var lastRequest: Any? = null
        var lastQuery: Any? = null

        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            lastQuery = query
            @Suppress("UNCHECKED_CAST")
            return when (query) {
                is ListIdeasQuery -> ListIdeasResponse(
                    ideas = listOf(
                        IdeaResult(
                            id = "idea-1",
                            workspaceId = "workspace-1",
                            title = "Idea 1",
                            notes = null,
                            tags = listOf("launch"),
                            links = emptyList(),
                            columnId = "raw",
                            orderInColumn = 0,
                            convertedToPublicationId = null,
                            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                            updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
                        ),
                    ),
                ) as TResponse

                is GetIdeaQuery -> IdeaResult(
                    id = query.ideaId,
                    workspaceId = "workspace-1",
                    title = "Idea 1",
                    notes = null,
                    tags = emptyList(),
                    links = emptyList(),
                    columnId = "raw",
                    orderInColumn = 0,
                    convertedToPublicationId = null,
                    createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
                ) as TResponse

                is GetColumnsQuery -> ColumnsResponse(
                    columns = listOf(
                        IdeaColumn("raw", "Raw", null, 0),
                        IdeaColumn("done", "Done", null, 1),
                    ),
                ) as TResponse

                else -> error("Unsupported query type ${query::class.simpleName}")
            }
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastRequest = command
            return when (command) {
                is CreateIdeaCommand,
                is UpdateIdeaCommand,
                is MoveIdeaCommand,
                -> IdeaResult(
                    id = "idea-1",
                    workspaceId = "workspace-1",
                    title = "Idea 1",
                    notes = null,
                    tags = emptyList(),
                    links = emptyList(),
                    columnId = "raw",
                    orderInColumn = 0,
                    convertedToPublicationId = null,
                    createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
                ) as TResult

                is com.profiletailors.smp.ideas.application.DeleteIdeaCommand -> IdeaResult(
                    id = command.ideaId,
                    workspaceId = "workspace-1",
                    title = "Idea 1",
                    notes = null,
                    tags = emptyList(),
                    links = emptyList(),
                    columnId = "raw",
                    orderInColumn = 0,
                    convertedToPublicationId = null,
                    createdAt = Instant.parse("2026-07-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
                ) as TResult

                is com.profiletailors.smp.ideas.application.ConvertIdeaCommand -> ConvertIdeaResult(
                    ideaId = command.ideaId,
                    publicationId = "pub-1",
                ) as TResult

                is UpdateColumnsCommand -> ColumnsResponse(command.columns) as TResult
                else -> error("Unsupported request type ${command::class.simpleName}")
            }
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used")
        }
    }
}
