package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.publishing.application.SocialContentCalendarItem
import com.profiletailors.smp.publishing.application.SocialContentCalendarResponse
import com.profiletailors.smp.publishing.application.SocialContentPostQuery
import com.profiletailors.smp.publishing.application.SocialContentSyncCommand
import com.profiletailors.smp.publishing.application.SocialContentSyncResult
import com.profiletailors.smp.publishing.application.SocialContentSyncStatus
import com.profiletailors.smp.publishing.application.WorkspaceSocialContentCalendarQuery
import com.profiletailors.smp.publishing.domain.ExternalPostId
import com.profiletailors.smp.publishing.domain.PageCursor
import com.profiletailors.smp.publishing.domain.PostLifecycle
import com.profiletailors.smp.publishing.domain.PostOrigin
import com.profiletailors.smp.publishing.domain.SocialPost
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class SocialContentControllersTest {
    private val mediator = StubMediator()
    private val client = WebTestClient
        .bindToController(SocialContentController(mediator))
        .controllerAdvice(PublishingProblemDetailsHandler())
        .apiVersioning {
            it.useVersionResolver(
                com.profiletailors.smp.platform.infrastructure.http.WebFluxConfiguration.MediaTypeVersionResolver(),
            )
        }
        .build()

    private fun WebTestClient.RequestHeadersSpec<*>.withV1Accept() =
        header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")

    @Test
    fun `GET calendar returns filtered social content page`() = runTest {
        mediator.nextQueryResult = SocialContentCalendarResponse(
            items = listOf(
                SocialContentCalendarItem(
                    externalPostId = ExternalPostId("post-1"),
                    scope = WorkspaceScope("workspace-1"),
                    provider = SocialProvider.LINKEDIN,
                    actorId = "page-1",
                    scheduledAt = null,
                    publishedAt = Instant.parse("2026-08-02T12:00:00Z"),
                    origin = PostOrigin.EXTERNAL_OR_UNKNOWN,
                    lifecycle = PostLifecycle.PUBLISHED,
                    mutationAllowed = false,
                ),
            ),
            nextCursor = PageCursor("cursor-2"),
        )

        client.get()
            .uri(
                "/api/publishing/social-content/calendar" +
                    "?from=2026-08-01T00:00:00Z" +
                    "&to=2026-08-08T00:00:00Z" +
                    "&actorId=page-1" +
                    "&lifecycle=PUBLISHED" +
                    "&cursor=cursor-1" +
                    "&limit=20",
            )
            .withV1Accept()
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items").isArray
            .jsonPath("$.items[0].externalPostId").isEqualTo("post-1")
            .jsonPath("$.items[0].publishedAt").isEqualTo("2026-08-02T12:00:00Z")
            .jsonPath("$.items[0].origin").isEqualTo("EXTERNAL_OR_UNKNOWN")
            .jsonPath("$.items[0].lifecycle").isEqualTo("PUBLISHED")
            .jsonPath("$.items[0].mutationAllowed").isEqualTo(false)
            .jsonPath("$.nextCursor").isEqualTo("cursor-2")

        mediator.lastQuery shouldBe WorkspaceSocialContentCalendarQuery(
            from = Instant.parse("2026-08-01T00:00:00Z"),
            to = Instant.parse("2026-08-08T00:00:00Z"),
            actorId = "page-1",
            lifecycle = PostLifecycle.PUBLISHED,
            cursor = PageCursor("cursor-1"),
            limit = 20,
        )
    }

    @Test
    fun `GET post returns social content post details`() = runTest {
        val post = SocialPost(
            scope = WorkspaceScope("workspace-1"),
            provider = SocialProvider.LINKEDIN,
            actorId = "page-1",
            externalPostId = ExternalPostId("post-1"),
            publishedAt = Instant.parse("2026-08-02T12:00:00Z"),
            origin = PostOrigin.EXTERNAL_OR_UNKNOWN,
            lifecycle = PostLifecycle.PUBLISHED,
            expiresAt = Instant.parse("2026-08-04T12:00:00Z"),
        )
        mediator.nextQueryResult = post

        client.get()
            .uri("/api/publishing/social-content/posts/{externalPostId}", "post-1")
            .withV1Accept()
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.externalPostId").isEqualTo("post-1")
            .jsonPath("$.actorId").isEqualTo("page-1")
            .jsonPath("$.publishedAt").isEqualTo("2026-08-02T12:00:00Z")
            .jsonPath("$.origin").isEqualTo("EXTERNAL_OR_UNKNOWN")
            .jsonPath("$.lifecycle").isEqualTo("PUBLISHED")

        mediator.lastQuery shouldBe SocialContentPostQuery("post-1")
    }

    @Test
    fun `POST sync returns successful sync result`() = runTest {
        mediator.nextCommandResult = SocialContentSyncResult(
            actorId = "page-1",
            importedCount = 2,
            highWaterMark = Instant.parse("2026-08-03T12:00:00Z"),
            status = SocialContentSyncStatus.COMPLETED,
        )

        client.post()
            .uri("/api/publishing/social-content/sync")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"actorId":"page-1"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.actorId").isEqualTo("page-1")
            .jsonPath("$.importedCount").isEqualTo(2)
            .jsonPath("$.status").isEqualTo("COMPLETED")

        mediator.lastCommand shouldBe SocialContentSyncCommand(actorId = "page-1")
    }

    @Test
    fun `GET calendar rejects invalid limit parameter`() = runTest {
        val invalidLimits = listOf(0, 150)

        invalidLimits.forEach { limit ->
            client.get()
                .uri(
                    "/api/publishing/social-content/calendar" +
                        "?from=2026-08-01T00:00:00Z" +
                        "&to=2026-08-08T00:00:00Z" +
                        "&limit=$limit",
                )
                .withV1Accept()
                .exchange()
                .expectStatus().isBadRequest
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Bad Request")
                .jsonPath("$.detail").isEqualTo("limit must be between 1 and 100, got $limit")
                .jsonPath("$.status").isEqualTo(400)
        }

        mediator.lastQuery.shouldBeNull()
    }

    @Test
    fun `POST sync rejects blank actorId`() = runTest {
        client.post()
            .uri("/api/publishing/social-content/sync")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"actorId":""}""")
            .exchange()
            .expectStatus().isBadRequest

        mediator.lastCommand.shouldBeNull()
    }

    @Test
    fun `social content controller endpoints map explicit version 1`() {
        val syncMethod = SocialContentController::class.java.declaredMethods.single { it.name == "sync" }
        val postMethod = SocialContentController::class.java.declaredMethods.single { it.name == "post" }
        val calendarMethod = SocialContentController::class.java.declaredMethods.single { it.name == "calendar" }

        val syncMapping = syncMethod.getAnnotation(org.springframework.web.bind.annotation.PostMapping::class.java)
        val postMapping = postMethod.getAnnotation(org.springframework.web.bind.annotation.GetMapping::class.java)
        val calendarMapping = calendarMethod.getAnnotation(
            org.springframework.web.bind.annotation.GetMapping::class.java,
        )

        syncMapping.version shouldBe "1"
        postMapping.version shouldBe "1"
        calendarMapping.version shouldBe "1"
    }

    @Test
    fun `version negotiation succeeds with vendor media type header`() = runTest {
        mediator.nextQueryResult = SocialContentCalendarResponse(items = emptyList(), nextCursor = null)

        client.get()
            .uri(
                "/api/publishing/social-content/calendar" +
                    "?from=2026-08-01T00:00:00Z" +
                    "&to=2026-08-08T00:00:00Z",
            )
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .exchange()
            .expectStatus().isOk

        mediator.lastQuery.shouldNotBeNull()
        (mediator.lastQuery as WorkspaceSocialContentCalendarQuery).limit shouldBe 50
    }

    private class StubMediator : Mediator {
        var lastCommand: Any? = null
        var nextCommandResult: Any? = null

        var lastQuery: Any? = null
        var nextQueryResult: Any? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            lastQuery = query
            return nextQueryResult as TResponse
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used in this test")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastCommand = command
            return nextCommandResult as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used in this test")
        }
    }
}
