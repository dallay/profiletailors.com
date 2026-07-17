package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.EvaluationStatus
import com.profiletailors.smp.governance.domain.EvaluationSummary
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class ComplianceControllerWebTest {

    private val mediator = StubMediator()
    private val controller = ComplianceController(mediator)
    private val client = WebTestClient.bindToController(controller).build()

    @Test
    fun `POST evaluations returns 200 with evaluation response`() {
        mediator.result = ComplianceEvaluation(
            context = ComplianceEvaluationContext(release = "mvp"),
            overallStatus = EvaluationStatus.COMPLIANT,
            summary = EvaluationSummary(
                totalControls = 3,
                passed = 2,
                failed = 1,
                waived = 0,
                notAssessed = 0,
                warnings = 0,
                notApplicable = 0,
            ),
            controlResults = emptyList(),
            evaluatedAt = Instant.parse("2026-07-17T12:00:00Z"),
            evaluatedBy = "system",
        )

        client.post().uri("/api/governance/compliance/evaluations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"context": {"release": "mvp", "market": "EEA"}}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.overallStatus").isEqualTo("COMPLIANT")
            .jsonPath("$.context.release").isEqualTo("mvp")
            .jsonPath("$.summary.totalControls").isEqualTo(3)
    }

    @Test
    fun `POST evaluations returns 400 when context field exceeds max length`() {
        val longValue = "x".repeat(101)

        client.post().uri("/api/governance/compliance/evaluations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"context": {"release": "$longValue"}}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST evaluations returns 400 when request body is malformed`() {
        client.post().uri("/api/governance/compliance/evaluations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("not-json")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `GET ping returns 200 with status ok`() {
        client.get().uri("/api/governance/compliance/ping")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
    }

    private class StubMediator : Mediator {
        lateinit var result: ComplianceEvaluation

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = result as TResponse

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used")
        }

        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used")
        }
    }
}
