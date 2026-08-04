package com.profiletailors.smp.hashtags.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.hashtags.application.DeleteHashtagSetCommand
import com.profiletailors.smp.hashtags.application.HashtagSavedSetNotFoundException
import com.profiletailors.smp.hashtags.application.HashtagSavedSetResult
import com.profiletailors.smp.hashtags.application.SaveHashtagSetCommand
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant

class HashtagsControllerTest {
    @Test
    fun `problem details map saved set validation errors`() {
        val handler = HashtagsProblemDetailsHandler()

        val notFound = handler.handleSavedSetNotFound(HashtagSavedSetNotFoundException("set-1"))
        assertEquals(HttpStatus.NOT_FOUND.value(), notFound.status)
        assertEquals("Hashtag set not found", notFound.title)
        assertEquals("set-1", notFound.properties?.get("setId"))

        val blank = handler.handleBlankName()
        assertEquals(HttpStatus.BAD_REQUEST.value(), blank.status)
        assertEquals("Invalid hashtag set", blank.title)

        val empty = handler.handleEmptySet()
        assertEquals(HttpStatus.BAD_REQUEST.value(), empty.status)
        assertEquals("Invalid hashtag set", empty.title)
    }

    @Test
    fun `save set returns created response explicitly`() = runTest {
        val result = HashtagSavedSetResult(
            id = "set-1",
            workspaceId = "workspace-1",
            name = "Tech",
            hashtags = listOf("#tech"),
            createdAt = Instant.parse("2026-08-02T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-02T12:00:00Z"),
        )
        val mediator = CapturingMediator(result)
        val controller = HashtagsController(mediator)

        val response = controller.saveSet(SaveHashtagSetRequest("Tech", listOf("#tech")))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(result, response.body)
        assertEquals(SaveHashtagSetCommand("Tech", listOf("#tech")), mediator.lastCommand)
    }

    @Test
    fun `delete set returns no content response explicitly`() = runTest {
        val mediator = CapturingMediator(null)
        val controller = HashtagsController(mediator)

        val response = controller.deleteSet("set-1")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals(DeleteHashtagSetCommand("set-1"), mediator.lastCommand)
    }

    @Test
    fun `delete set returns a unit response type`() = runTest {
        val controller = HashtagsController(CapturingMediator(null))

        val response = controller.deleteSet("set-1")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    private class CapturingMediator(private val result: Any?) : Mediator {
        var lastCommand: Any? = null

        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse =
            error("Not used in this test")

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used in this test")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastCommand = command
            return result as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used in this test")
        }
    }
}
