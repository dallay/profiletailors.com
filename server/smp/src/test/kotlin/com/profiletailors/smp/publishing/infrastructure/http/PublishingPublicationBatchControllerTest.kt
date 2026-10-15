package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.publishing.application.BatchPublicationStatus
import com.profiletailors.smp.publishing.application.BatchPublicationTargetResult
import com.profiletailors.smp.publishing.application.CreatePublicationBatchCommand
import com.profiletailors.smp.publishing.application.CreatePublicationBatchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class PublishingPublicationBatchControllerTest {
    @Test
    fun `should reject duplicate social accounts when request contains them`() {
        val mediator = mockk<Mediator>()
        val client = WebTestClient
            .bindToController(PublishingPublicationController(mediator))
            .controllerAdvice(PublishingProblemDetailsHandler())
            .apiVersioning {
                it.useVersionResolver(
                    com.profiletailors.smp.platform.infrastructure.http.WebFluxConfiguration.MediaTypeVersionResolver(),
                )
            }
            .build()

        client.post()
            .uri("/api/publishing/publications/batch")
            .header(HttpHeaders.ACCEPT, "application/vnd.api.v1+json")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"targets":[{"socialAccountId":"account-1"},{"socialAccountId":"account-1"}],"scheduleMode":"NOW"}
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isBadRequest

        coVerify(exactly = 0) { mediator.send(any<CreatePublicationBatchCommand>()) }
    }

    @Test
    fun `should return per-target failures when mediator reports all targets failed`() = runTest {
        val mediator = mockk<Mediator>()
        coEvery { mediator.send(any<CreatePublicationBatchCommand>()) } returns CreatePublicationBatchResult(
            status = BatchPublicationStatus.ALL_FAILED,
            results = listOf(
                BatchPublicationTargetResult(
                    socialAccountId = "account-1",
                    status = "FAILED",
                    errorCode = "INVALID_CONTENT",
                    groupId = "pub-group-1",
                    errorMessage = "Content rejected",
                ),
            ),
        )
        val controller = PublishingPublicationController(mediator)

        val response = controller.createPublicationBatch(
            PublicationBatchCreateRequest(
                targets = listOf(PublicationBatchTargetRequest(socialAccountId = "account-1", bodyText = "Copy")),
                scheduleMode = "NOW",
            ),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("ALL_FAILED", response.body?.status)
        assertEquals("INVALID_CONTENT", response.body?.results?.single()?.errorCode)
        assertEquals("pub-group-1", response.body?.results?.single()?.groupId)
    }
}
