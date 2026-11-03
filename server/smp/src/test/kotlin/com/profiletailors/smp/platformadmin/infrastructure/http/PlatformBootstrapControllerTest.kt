package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.identity.application.CurrentTermsVersion
import com.profiletailors.smp.identity.application.GetCurrentTermsVersionQuery
import com.profiletailors.smp.platformadmin.application.CompleteBootstrapCommand
import com.profiletailors.smp.platformadmin.application.CompleteBootstrapResult
import com.profiletailors.smp.platformadmin.domain.BootstrapUnavailableException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class PlatformBootstrapControllerTest {
    private val mediator: Mediator = mockk()
    private val controller = PlatformBootstrapController(mediator)
    private val client = WebTestClient.bindToController(controller)
        .controllerAdvice(AdminProblemDetailsHandler())
        .build()

    @Test
    fun `requirements exposes only current terms version`() {
        coEvery { mediator.send(any<GetCurrentTermsVersionQuery>()) } returns CurrentTermsVersion("terms-v1.0.0")

        client.get().uri("/api/bootstrap/requirements")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentTermsVersion").isEqualTo("terms-v1.0.0")
            .jsonPath("$.invitation").doesNotExist()
            .jsonPath("$.email").doesNotExist()
    }

    @Test
    fun `complete accepts only the recipient fields and returns empty accepted response`() {
        coEvery { mediator.send(any<CompleteBootstrapCommand>()) } returns CompleteBootstrapResult

        client.post().uri("/api/bootstrap/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "invitationToken": "single-use-token",
                  "password": "bootstrap-test-password",
                  "acceptedTermsVersion": "terms-v1.0.0"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isAccepted
            .expectBody().isEmpty

        coVerify(exactly = 1) {
            mediator.send(
                match<CompleteBootstrapCommand> {
                    it.invitationToken == "single-use-token" &&
                        it.password == "bootstrap-test-password" &&
                        it.acceptedTermsVersion == "terms-v1.0.0"
                },
            )
        }
    }

    @Test
    fun `unusable credentials return one generic problem code`() {
        coEvery { mediator.send(any<CompleteBootstrapCommand>()) } throws BootstrapUnavailableException()

        client.post().uri("/api/bootstrap/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "invitationToken": "invalid-token",
                  "password": "bootstrap-test-password",
                  "acceptedTermsVersion": "terms-v1.0.0"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("BOOTSTRAP_UNAVAILABLE")
    }
}
