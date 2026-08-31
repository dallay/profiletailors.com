@file:Suppress("MaxLineLength")

package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.application.BulkJobResult
import com.profiletailors.smp.publishing.application.BulkTemplateCsvQuery
import com.profiletailors.smp.publishing.application.BulkTemplateCsvResult
import com.profiletailors.smp.publishing.application.BulkTemplateItem
import com.profiletailors.smp.publishing.application.BulkTemplatesQuery
import com.profiletailors.smp.publishing.application.BulkTemplatesResult
import com.profiletailors.smp.publishing.application.BulkWorkspaceMismatchException
import com.profiletailors.smp.publishing.application.DuplicateBulkImportException
import com.profiletailors.smp.publishing.application.GetBulkJobQuery
import com.profiletailors.smp.publishing.application.ScheduleBulkCommand
import com.profiletailors.smp.publishing.application.ScheduleBulkResult
import com.profiletailors.smp.publishing.application.ValidateBulkCommand
import com.profiletailors.smp.publishing.application.ValidateBulkResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class BulkPublishingControllerTest {

    @Test
    fun `validate delegates to mediator with workspace id`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextValidateResult = ValidateBulkResult(emptyList())
        val result = controller.validate(
            "workspace-1",
            BulkValidateRequest(
                csvText = "bodyText,scheduledFor,timezone,media_urls,hashtags\nHi,2026-02-01T12:00:00Z,UTC,,",
            ),
        )
        assertEquals(0, result.rows.size)
        val cmd = mediator.lastCommand as ValidateBulkCommand
        assertEquals("workspace-1", cmd.workspaceId)
    }

    @Test
    fun `validate throws workspace mismatch when path differs`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        assertThrows(BulkWorkspaceMismatchException::class.java) {
            kotlinx.coroutines.runBlocking {
                controller.validate("workspace-other", BulkValidateRequest(csvText = "csv"))
            }
        }
    }

    @Test
    fun `validate throws workspace mismatch when context missing IllegalState`() = runTest {
        val mediator = CapturingMediator()
        val controller =
            BulkPublishingController(mediator, ThrowingResourceContextProvider(IllegalStateException("no context")))
        assertThrows(BulkWorkspaceMismatchException::class.java) {
            kotlinx.coroutines.runBlocking { controller.validate("workspace-1", BulkValidateRequest(csvText = "csv")) }
        }
    }

    @Test
    fun `validate throws workspace mismatch when context throws IllegalArgument`() = runTest {
        val mediator = CapturingMediator()
        val controller =
            BulkPublishingController(mediator, ThrowingResourceContextProvider(IllegalArgumentException("bad")))
        assertThrows(BulkWorkspaceMismatchException::class.java) {
            kotlinx.coroutines.runBlocking { controller.validate("workspace-1", BulkValidateRequest(csvText = "csv")) }
        }
    }

    @Test
    fun `schedule returns 200 when all scheduled`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextScheduleResult = ScheduleBulkResult("job-1", 2, 2, 0, emptyList())
        val response = controller.schedule(
            "workspace-1",
            BulkScheduleRequest(csvText = "csv", csvHash = "a".repeat(64)),
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("job-1", response.body!!.jobId)
    }

    @Test
    fun `schedule returns MULTI_STATUS 207 when partial`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextScheduleResult = ScheduleBulkResult("job-1", 2, 1, 1, emptyList())
        val response = controller.schedule(
            "workspace-1",
            BulkScheduleRequest(csvText = "csv", csvHash = "a".repeat(64)),
        )
        assertEquals(HttpStatus.MULTI_STATUS, response.statusCode)
    }

    @Test
    fun `schedule returns 200 when only failed`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextScheduleResult = ScheduleBulkResult("job-1", 2, 0, 2, emptyList())
        val response = controller.schedule("workspace-1", BulkScheduleRequest(csvText = "csv"))
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `schedule uses csvText as hash fallback when csvHash null`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextScheduleResult = ScheduleBulkResult("job-1", 0, 0, 0, emptyList())
        val response = controller.schedule("workspace-1", BulkScheduleRequest(csvText = "my-csv"))
        assertEquals(HttpStatus.OK, response.statusCode)
        val cmd = mediator.lastCommand as ScheduleBulkCommand
        assertEquals("my-csv", cmd.csvHash)
        assertEquals("my-csv", cmd.csvText)
    }

    @Test
    fun `schedule returns 409 CONFLICT on DuplicateBulkImportException`() = runTest {
        val mediator = CapturingMediator()
        mediator.throwDuplicate = DuplicateBulkImportException("job-dup")
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        val response = controller.schedule(
            "workspace-1",
            BulkScheduleRequest(csvText = "csv", csvHash = "a".repeat(64)),
        )
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("job-dup", response.body!!.jobId)
        assertEquals(0, response.body!!.totalRows)
    }

    @Test
    fun `schedule workspace mismatch throws 403`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        assertThrows(BulkWorkspaceMismatchException::class.java) {
            kotlinx.coroutines.runBlocking {
                controller.schedule("workspace-other", BulkScheduleRequest(csvText = "csv"))
            }
        }
    }

    @Test
    fun `getJob delegates to mediator`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextBulkJobResult = BulkJobResult("job-1", "SCHEDULED", 1, 1, 0, emptyList())
        val result = controller.getJob("workspace-1", "job-1")
        assertEquals("job-1", result.jobId)
        val q = mediator.lastQuery as GetBulkJobQuery
        assertEquals("workspace-1", q.workspaceId)
        assertEquals("job-1", q.jobId)
    }

    @Test
    fun `listTemplates delegates to mediator`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextTemplatesResult = BulkTemplatesResult(listOf(BulkTemplateItem("id", "name", "desc", "header")))
        val result = controller.listTemplates("workspace-1")
        assertEquals(1, result.templates.size)
        val q = mediator.lastQuery as BulkTemplatesQuery
        assertEquals("workspace-1", q.workspaceId)
    }

    @Test
    fun `getTemplateCsv returns text csv with 200`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        mediator.nextCsvResult =
            BulkTemplateCsvResult(
                csv = "bodyText,scheduledFor,timezone,media_urls,hashtags\n",
                header = "bodyText,scheduledFor,timezone,media_urls,hashtags",
            )
        val response = controller.getTemplateCsv("workspace-1", "linkedin-calendar")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.parseMediaType("text/csv"), response.headers.contentType)
        assertEquals("bodyText,scheduledFor,timezone,media_urls,hashtags\n", response.body)
        val q = mediator.lastQuery as BulkTemplateCsvQuery
        assertEquals("linkedin-calendar", q.templateId)
    }

    @Test
    fun `getTemplateCsv workspace mismatch throws`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        assertThrows(BulkWorkspaceMismatchException::class.java) {
            kotlinx.coroutines.runBlocking { controller.getTemplateCsv("workspace-other", "linkedin-calendar") }
        }
    }

    @Test
    fun `listTemplates workspace mismatch throws`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        assertThrows(BulkWorkspaceMismatchException::class.java) {
            kotlinx.coroutines.runBlocking { controller.listTemplates("workspace-other") }
        }
    }

    @Test
    fun `getJob workspace mismatch throws`() = runTest {
        val mediator = CapturingMediator()
        val controller = BulkPublishingController(mediator, FixedResourceContextProvider("workspace-1"))
        assertThrows(BulkWorkspaceMismatchException::class.java) {
            kotlinx.coroutines.runBlocking { controller.getJob("workspace-other", "job-1") }
        }
    }

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
    }

    private class ThrowingResourceContextProvider(private val ex: RuntimeException) : ResourceContextProvider {
        override fun current(): ResourceContext = throw ex
    }

    private class CapturingMediator : Mediator {
        var lastCommand: Any? = null
        var lastQuery: Any? = null
        var nextValidateResult: ValidateBulkResult = ValidateBulkResult(emptyList())
        var nextScheduleResult: ScheduleBulkResult = ScheduleBulkResult("job-1", 0, 0, 0, emptyList())
        var nextBulkJobResult: BulkJobResult = BulkJobResult("job-1", "PENDING", 0, 0, 0, emptyList())
        var nextTemplatesResult: BulkTemplatesResult = BulkTemplatesResult(emptyList())
        var nextCsvResult: BulkTemplateCsvResult = BulkTemplateCsvResult("", "")
        var throwDuplicate: DuplicateBulkImportException? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            lastQuery = query
            return when (query) {
                is GetBulkJobQuery -> nextBulkJobResult as TResponse
                is BulkTemplatesQuery -> nextTemplatesResult as TResponse
                is BulkTemplateCsvQuery -> nextCsvResult as TResponse
                else -> error("unsupported query $query")
            }
        }

        override suspend fun <TCommand : Command> send(command: TCommand) = error("not used")

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastCommand = command
            throwDuplicate?.let { throw it }
            return when (command) {
                is ValidateBulkCommand -> nextValidateResult as TResult
                is ScheduleBulkCommand -> nextScheduleResult as TResult
                else -> error("unsupported command $command")
            }
        }

        override suspend fun <T : Notification> publish(notification: T) = error("not used")
        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) =
            error("not used")
    }
}
