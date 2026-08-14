package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.AcceptInvitationHandler
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class InvitationAcceptanceControllerTest {
    private val acceptInvitationHandler = mockk<AcceptInvitationHandler>()
    private val requestContextStore = mockk<RequestContextStore>()

    @Test
    fun `accept returns safe invitation result for authenticated principal`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns principalWithEmailAttribute()
        coEvery { acceptInvitationHandler.handle(any()) } returns
            InvitationAcceptanceResult(
                workspaceId = "workspace-123",
                membershipStatus = "ACTIVE",
            )

        webClient()
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"raw-invitation-token"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-123")
            .jsonPath("$.membershipStatus").isEqualTo("ACTIVE")
            .jsonPath("$.token").doesNotExist()
            .jsonPath("$.email").doesNotExist()

        coVerify {
            acceptInvitationHandler.handle(
                match {
                    it.rawToken == "raw-invitation-token" &&
                        it.authenticatedPrincipalId == "user-123" &&
                        it.authenticatedEmail == "invitee@example.com"
                },
            )
        }
    }

    @Test
    fun `accept uses subject when authenticated email claim is absent`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns principal()
        coEvery { acceptInvitationHandler.handle(any()) } returns
            InvitationAcceptanceResult("workspace-123", "ACTIVE")

        webClient()
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"raw-invitation-token"}""")
            .exchange()
            .expectStatus().isOk

        coVerify {
            acceptInvitationHandler.handle(match { it.authenticatedEmail == "invitee@example.com" })
        }
    }

    @Test
    fun `accept returns 401 without an authenticated principal`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns null

        webClient()
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"raw-invitation-token"}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `accept returns 400 for a blank token without calling the handler`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns principal()

        webClient()
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"   "}""")
            .exchange()
            .expectStatus().isBadRequest

        coVerify(exactly = 0) { acceptInvitationHandler.handle(any()) }
    }

    private fun webClient(): WebTestClient = WebTestClient
        .bindToController(InvitationAcceptanceController(acceptInvitationHandler, requestContextStore))
        .controllerAdvice(AdminProblemDetailsHandler())
        .build()

    private fun principal() = PrincipalContext(
        principalId = "user-123",
        principalType = PrincipalType.USER,
        subject = "invitee@example.com",
        provider = "jwt",
    )

    private fun principalWithEmailAttribute() = principal().copy(
        subject = "subject-123",
        attributes = mapOf("email" to "invitee@example.com"),
    )
}
