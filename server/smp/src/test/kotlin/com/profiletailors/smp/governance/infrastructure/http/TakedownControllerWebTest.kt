package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class TakedownControllerWebTest {

    private val mediator = StubbedTakedownMediator()
    private val controller = TakedownController(mediator)
    private val advice = GovernanceProblemDetailsHandler()
    private val client = WebTestClient.bindToController(controller).controllerAdvice(advice).build()

    @Test
    fun `POST reports returns 201 with report response`() {
        mediator.result = TakedownReport(
            reportId = "report-001",
            workspaceId = "ws-001",
            assetId = "asset-001",
            reportedById = "user-001",
            reason = "Copyright infringement",
            status = TakedownReportStatus.REPORTED,
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = "https://example.test/original",
            createdAt = Instant.parse("2026-07-21T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
        )

        client.post().uri("/api/governance/takedown/reports")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "assetId": "asset-001",
                    "reason": "Copyright infringement",
                    "reporterEmail": "reporter@example.com",
                    "mediaReferenceUrl": "https://example.test/original"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.reportId").isEqualTo("report-001")
            .jsonPath("$.assetId").isEqualTo("asset-001")
            .jsonPath("$.status").isEqualTo("REPORTED")
            .jsonPath("$.reason").isEqualTo("Copyright infringement")
            .jsonPath("$.reporterEmail").isEqualTo("reporter@example.com")
    }

    @Test
    fun `POST reports returns 400 when request body is invalid`() {
        client.post().uri("/api/governance/takedown/reports")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "assetId": "",
                    "reason": "",
                    "reporterEmail": ""
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST reports returns 400 when request body is malformed`() {
        client.post().uri("/api/governance/takedown/reports")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("not-json")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST reports reportId approve returns 200 with approved report`() {
        mediator.result = TakedownReport(
            reportId = "report-001",
            workspaceId = "ws-001",
            assetId = "asset-001",
            reportedById = "user-001",
            reason = "Copyright infringement",
            status = TakedownReportStatus.APPROVED,
            reporterEmail = "reporter@example.com",
            reviewedById = "reviewer-001",
            reviewedAt = Instant.parse("2026-07-21T10:00:00Z"),
            createdAt = Instant.parse("2026-07-21T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T10:00:00Z"),
        )

        client.post().uri("/api/governance/takedown/reports/report-001/approve")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.reportId").isEqualTo("report-001")
            .jsonPath("$.status").isEqualTo("APPROVED")
            .jsonPath("$.reviewedById").isEqualTo("reviewer-001")
    }

    @Test
    fun `POST reports reportId reject returns 200 with dismissed report`() {
        mediator.result = TakedownReport(
            reportId = "report-001",
            workspaceId = "ws-001",
            assetId = "asset-001",
            reportedById = "user-001",
            reason = "Copyright infringement",
            status = TakedownReportStatus.DISMISSED,
            reporterEmail = "reporter@example.com",
            reviewedById = "reviewer-001",
            reviewedAt = Instant.parse("2026-07-21T10:00:00Z"),
            rejectionReason = "Not valid claim",
            createdAt = Instant.parse("2026-07-21T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T10:00:00Z"),
        )

        client.post().uri("/api/governance/takedown/reports/report-001/reject")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"rejectionReason": "Not valid claim"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.reportId").isEqualTo("report-001")
            .jsonPath("$.status").isEqualTo("DISMISSED")
            .jsonPath("$.rejectionReason").isEqualTo("Not valid claim")
    }

    @Test
    fun `GET reports returns 200 with list of reports`() {
        mediator.result = flowOf(
            TakedownReport(
                reportId = "report-001",
                workspaceId = "ws-001",
                assetId = "asset-001",
                reportedById = "user-001",
                reason = "Copyright infringement",
                status = TakedownReportStatus.REPORTED,
                reporterEmail = "reporter@example.com",
                createdAt = Instant.parse("2026-07-21T09:00:00Z"),
                updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
            ),
        )

        client.get().uri("/api/governance/takedown/reports")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].reportId").isEqualTo("report-001")
            .jsonPath("$[0].status").isEqualTo("REPORTED")
    }

    @Test
    fun `GET reports with valid status filter returns 200`() {
        mediator.result = flowOf<TakedownReport>()

        client.get().uri("/api/governance/takedown/reports?status=REPORTED")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `GET reports with invalid status returns 400`() {
        client.get().uri("/api/governance/takedown/reports?status=INVALID")
            .exchange()
            .expectStatus().isBadRequest
    }

    private class StubbedTakedownMediator : Mediator {
        lateinit var result: Any

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = result as TResponse

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used")
        }

        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            @Suppress("UNCHECKED_CAST")
            return result as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used")
        }
    }
}
