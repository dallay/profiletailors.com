package com.profiletailors.smp.privacy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.privacy.application.CheckRequestStatusQuery
import com.profiletailors.smp.privacy.application.DataSubjectRequestResponse
import com.profiletailors.smp.privacy.application.ListRequestsQuery
import com.profiletailors.smp.privacy.application.SubmitAccessRequestCommand
import com.profiletailors.smp.privacy.application.SubmitCorrectionRequestCommand
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class PrivacyControllerWebTest {

    private val mediator = StubMediator()
    private val principalContextProvider = FakePrincipalContextProvider()
    private val rateLimiter = CountingRateLimiter(allowedBeforeBlock = 3)
    private val controller = PrivacyController(
        mediator = mediator,
        principalContextProvider = principalContextProvider,
        rateLimiter = rateLimiter,
    )
    private val client = WebTestClient.bindToController(controller).build()

    private val now = Instant.parse("2026-07-19T10:00:00Z")

    private val sampleResponse = DataSubjectRequestResponse(
        id = "f3812fbf-7066-56eb-b183-2c0c94bd3985",
        type = "ACCESS",
        status = "COMPLETED",
        requestedBy = "test-principal",
        requestedByEmail = "test@example.com",
        workspaceId = null,
        resultRef = null,
        rejectionReason = null,
        createdAt = now,
        updatedAt = now,
        completedAt = now,
    )

    @Test
    fun `POST create request returns 201`() {
        mediator.nextCommandResult = sampleResponse

        client.post().uri("/api/v1/privacy/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type": "ACCESS", "notes": "my data please"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo("f3812fbf-7066-56eb-b183-2c0c94bd3985")
            .jsonPath("$.status").isEqualTo("COMPLETED")
            .jsonPath("$.message").isEqualTo("Request submitted successfully")
    }

    @Test
    fun `POST create request dispatches correct command`() {
        mediator.nextCommandResult = sampleResponse

        client.post().uri("/api/v1/privacy/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type": "ACCESS", "notes": "my data"}""")
            .exchange()
            .expectStatus().isCreated

        val command = requireNotNull(mediator.lastSent as? SubmitAccessRequestCommand) {
            "Expected SubmitAccessRequestCommand"
        }
        kotlin.test.assertEquals("test-principal", command.requestedByPrincipalId)
        kotlin.test.assertEquals("test@example.com", command.requestedByEmail)
        kotlin.test.assertEquals("my data", command.notes)
    }

    @Test
    fun `POST correction request dispatches correction command`() {
        mediator.nextCommandResult = sampleResponse.copy(
            id = "50ef9cd3-0a03-55f0-aa08-a15ce94c1c6f",
            type = "CORRECTION",
        )

        client.post().uri("/api/v1/privacy/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type": "CORRECTION", "newEmail": "new@example.com"}""")
            .exchange()
            .expectStatus().isCreated

        val command = requireNotNull(mediator.lastSent as? SubmitCorrectionRequestCommand) {
            "Expected SubmitCorrectionRequestCommand"
        }
        kotlin.test.assertEquals(com.profiletailors.smp.privacy.application.CorrectionField.EMAIL, command.field)
        kotlin.test.assertEquals("new@example.com", command.newValue)
    }

    @Test
    fun `POST with invalid type returns 400`() {
        client.post().uri("/api/v1/privacy/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type": "INVALID"}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `GET list requests returns 200`() {
        val requests = listOf(
            sampleResponse.copy(id = "4eac5319-6c43-5aff-b8e3-fcfb0f7fcca5"),
            sampleResponse.copy(id = "0548b7a9-bdd5-5ce8-b2da-7a78c406d107", type = "EXPORT"),
        )
        mediator.listQueryResult = requests

        client.get().uri("/api/v1/privacy/requests")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.total").isEqualTo(2)
            .jsonPath("$.page").isEqualTo(1)
            .jsonPath("$.perPage").isEqualTo(2)
            .jsonPath("$.requests[0].id").isEqualTo("4eac5319-6c43-5aff-b8e3-fcfb0f7fcca5")
            .jsonPath("$.requests[1].id").isEqualTo("0548b7a9-bdd5-5ce8-b2da-7a78c406d107")
    }

    @Test
    fun `GET request by id returns 200`() {
        mediator.statusQueryResultById = mapOf("b2b09298-7523-599f-b289-7c09a0b10264" to sampleResponse)

        client.get().uri("/api/v1/privacy/requests/b2b09298-7523-599f-b289-7c09a0b10264")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo("f3812fbf-7066-56eb-b183-2c0c94bd3985")
            .jsonPath("$.type").isEqualTo("ACCESS")
            .jsonPath("$.status").isEqualTo("COMPLETED")
    }

    @Test
    fun `GET request by non-existent id returns 404`() {
        mediator.statusQueryResultById = emptyMap()

        client.get().uri("/api/v1/privacy/requests/e2dc30b4-0019-5c96-a7b0-2a29bf825320")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `rate limit blocks 4th request`() {
        val limitedRateLimiter = CountingRateLimiter(allowedBeforeBlock = 3)

        val limitedController = PrivacyController(
            mediator = mediator,
            principalContextProvider = principalContextProvider,
            rateLimiter = limitedRateLimiter,
        )
        val limitedClient = WebTestClient.bindToController(limitedController).build()

        // First 3 requests should succeed
        for (i in 1..3) {
            mediator.nextCommandResult = sampleResponse.copy(id = "dsr-$i")
            limitedClient.post().uri("/api/v1/privacy/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"type": "ACCESS"}""")
                .exchange()
                .expectStatus().isCreated
        }

        // 4th request should be blocked
        limitedClient.post().uri("/api/v1/privacy/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type": "ACCESS"}""")
            .exchange()
            .expectStatus().isEqualTo(429)
    }

    // ——————— Test doubles ———————

    private class StubMediator : Mediator {
        var nextCommandResult: Any? = null
        var listQueryResult: List<DataSubjectRequestResponse> = emptyList()
        var statusQueryResultById: Map<String, DataSubjectRequestResponse?> = emptyMap()
        var lastSent: Any? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = when (query) {
            is ListRequestsQuery -> listQueryResult as TResponse
            is CheckRequestStatusQuery -> statusQueryResultById[query.requestId] as TResponse
            else -> error("Unknown query: $query")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastSent = command
            return nextCommandResult as TResult
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used")
        }
    }

    private class FakePrincipalContextProvider : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "test-principal",
            principalType = PrincipalType.USER,
            subject = "test@example.com",
        )
    }

    private class CountingRateLimiter(private val allowedBeforeBlock: Int) : RateLimiter {
        private var count = 0

        override fun tryAcquire(requesterId: String): Boolean {
            if (count >= allowedBeforeBlock) return false
            count++
            return true
        }
    }
}
