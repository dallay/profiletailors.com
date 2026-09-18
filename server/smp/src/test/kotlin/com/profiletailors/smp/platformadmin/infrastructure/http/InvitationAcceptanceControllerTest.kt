package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.RateLimit
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.AcceptInvitationHandler
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceResult
import com.profiletailors.smp.platformadmin.infrastructure.BCryptTokenHasher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `accept rejects an authenticated non-user principal without calling the handler`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns principal().copy(
            principalType = PrincipalType.SERVICE_ACCOUNT,
        )

        webClient()
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"raw-invitation-token"}""")
            .exchange()
            .expectStatus().isForbidden

        coVerify(exactly = 0) { acceptInvitationHandler.handle(any()) }
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

    @Test
    fun `accept returns 429 with safe code when per-key throttle denies without calling the handler`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns principal()
        val denied = RateLimit { _, _, _ -> false }

        webClient(rateLimit = denied)
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"raw-invitation-token"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVITATION_RATE_LIMIT_EXCEEDED")
            .jsonPath("$.status").isEqualTo(429)
            .jsonPath("$.detail").isEqualTo("Invitation accept rate limit exceeded. Try again later.")

        coVerify(exactly = 0) { acceptInvitationHandler.handle(any()) }
    }

    @Test
    fun `accept throttle key binds candidate key without raw token material`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns principal()
        coEvery { acceptInvitationHandler.handle(any()) } returns
            InvitationAcceptanceResult("workspace-123", "ACTIVE")
        val throttleKey = slot<String>()
        val admitted = RateLimit { key, _, _ ->
            throttleKey.captured = key
            true
        }

        webClient(rateLimit = admitted)
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"raw-invitation-token"}""")
            .exchange()
            .expectStatus().isOk

        val expectedCandidateKey = BCryptTokenHasher().candidateKey("raw-invitation-token")
        assertTrue(throttleKey.captured.contains(expectedCandidateKey))
        assertFalse(throttleKey.captured.contains("raw-invitation-token"))
    }

    @Test
    fun `accept throttle locks attempt bounds to ten per ten minutes`() {
        coEvery { requestContextStore.currentPrincipalContext() } returns principal()
        coEvery { acceptInvitationHandler.handle(any()) } returns
            InvitationAcceptanceResult("workspace-123", "ACTIVE")
        val throttleWindow = slot<Duration>()
        val throttleMax = slot<Int>()
        val bounding = object : RateLimit {
            override fun tryAcquire(key: String, window: Duration, now: Instant): Boolean = true

            override fun tryAcquire(key: String, window: Duration, now: Instant, maxRequests: Int): Boolean {
                throttleWindow.captured = window
                throttleMax.captured = maxRequests
                return true
            }
        }

        webClient(rateLimit = bounding)
            .post()
            .uri("/api/invitations/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"token":"raw-invitation-token"}""")
            .exchange()
            .expectStatus().isOk

        assertEquals(Duration.ofMinutes(10), throttleWindow.captured)
        assertEquals(10, throttleMax.captured)
    }

    private fun webClient(rateLimit: RateLimit = RateLimit { _, _, _ -> true }): WebTestClient = WebTestClient
        .bindToController(
            InvitationAcceptanceController(
                acceptInvitationHandler = acceptInvitationHandler,
                requestContextStore = requestContextStore,
                acceptAttemptRateLimit = rateLimit,
                invitationTokenCandidateKey = BCryptTokenHasher(),
                clock = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC),
            ),
        )
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
