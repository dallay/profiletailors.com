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
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Documents the shape of the `result` payload returned by the GET status endpoint.
 *
 * Today the controller wraps `DataSubjectRequestResponse.resultRef` as
 * `mapOf("ref" to resultRef)` because the DTO is declared as `Map<String, Any?>`. That
 * is a typing hole — there is no way for a consumer to know what fields the map
 * contains without reading the controller code. These tests pin the contract so we can
 * replace it with a typed value object.
 */
class PrivacyRequestResultMappingTest {

    private val mediator = StubMediator()
    private val principalContextProvider = StubPrincipalContextProvider()
    private val rateLimiter = AlwaysAllowingRateLimiter()
    private val controller = PrivacyController(
        mediator = mediator,
        principalContextProvider = principalContextProvider,
        rateLimiter = rateLimiter,
    )
    private val client = WebTestClient.bindToController(controller).build()

    private val now = Instant.parse("2026-07-19T10:00:00Z")

    @Test
    fun `result object exposes the single field 'ref' when resultRef is set`() {
        val ref = "https://storage.example.com/export-abc.json"
        mediator.statusQueryResultById = mapOf(
            "dsr-1" to sampleResponse().copy(resultRef = ref),
        )

        client.get().uri("/api/v1/privacy/requests/dsr-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result.ref").isEqualTo(ref)
    }

    @Test
    fun `result object is null when resultRef is null`() {
        mediator.statusQueryResultById = mapOf(
            "dsr-2" to sampleResponse().copy(resultRef = null),
        )

        client.get().uri("/api/v1/privacy/requests/dsr-2")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.result").doesNotExist()
    }

    @Test
    fun `mapping roundtrips from DataSubjectRequestResponse to PrivacyRequestResult`() {
        val ref = "s3://bucket/key"
        val source = sampleResponse().copy(resultRef = ref)

        val mapped: PrivacyRequestResult? = PrivacyController.toRequestResult(source)

        assertEquals(PrivacyRequestResult(ref = ref), mapped)
    }

    @Test
    fun `mapping produces null when resultRef is null`() {
        val source = sampleResponse().copy(resultRef = null)

        val mapped: PrivacyRequestResult? = PrivacyController.toRequestResult(source)

        assertNull(mapped)
    }

    private fun sampleResponse(): DataSubjectRequestResponse = DataSubjectRequestResponse(
        id = "dsr-test-123",
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

    // ——————— Test doubles ———————

    private class StubMediator : Mediator {
        var statusQueryResultById: Map<String, DataSubjectRequestResponse?> = emptyMap()
        var listQueryResult: List<DataSubjectRequestResponse> = emptyList()
        var nextCommandResult: Any? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = when (query) {
            is ListRequestsQuery -> listQueryResult as TResponse
            is CheckRequestStatusQuery -> statusQueryResultById[query.requestId] as TResponse
            else -> error("Unknown query: $query")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            nextCommandResult as TResult
            @Suppress("UNCHECKED_CAST")
            return null as TResult
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

    private class StubPrincipalContextProvider : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "test-principal",
            principalType = PrincipalType.USER,
            subject = "test@example.com",
        )
    }

    private class AlwaysAllowingRateLimiter : RateLimiter {
        override fun tryAcquire(requesterId: String): Boolean = true
    }
}
