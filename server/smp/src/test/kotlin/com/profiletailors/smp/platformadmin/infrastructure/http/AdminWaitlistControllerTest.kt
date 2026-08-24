package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.handler.CancelWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.InviteWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntryDetail
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntrySummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.ports.AdminWaitlistQuery
import com.profiletailors.smp.platformadmin.application.ports.WaitlistQueryTelemetryPort
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminWaitlistControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val entryId = "entry-abc-123"

    private val waitlistQuery = mockk<AdminWaitlistQuery>()
    private val inviteHandler = mockk<InviteWaitlistEntryHandler>(relaxed = true)
    private val cancelHandler = mockk<CancelWaitlistEntryHandler>(relaxed = true)
    private val operatorAccessResolver = mockk<OperatorAccessResolver>()
    private val waitlistQueryTelemetry = mockk<WaitlistQueryTelemetryPort>(relaxed = true)

    @Test
    fun `listEntries returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/waitlist-entries")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `listEntries returns 403 when operator lacks waitlist read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `listEntries returns entries and forwards filters to query`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.list(any()) } returns PagedResult.of(listOf(summary()), 0, 25, 1)

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries?status=PENDING&waitlistId=waitlist-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].id").isEqualTo(entryId)
            .jsonPath("$.items[0].status").isEqualTo("PENDING")
            .jsonPath("$.totalElements").isEqualTo(1)

        coVerify {
            waitlistQuery.list(
                match { query -> query.status == "PENDING" && query.waitlistId == "waitlist-1" },
            )
        }
        verify {
            waitlistQueryTelemetry.recordListQuery(
                statusFilterApplied = true,
                emailSearch = false,
            )
        }
    }

    @Test
    fun `listEntries records telemetry for unfiltered request`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.list(any()) } returns PagedResult.of(emptyList(), 0, 25, 0)

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries")
            .exchange()
            .expectStatus().isOk

        verify {
            waitlistQueryTelemetry.recordListQuery(
                statusFilterApplied = false,
                emailSearch = false,
            )
        }
    }

    @Test
    fun `listEntries returns 400 when size exceeds max page size`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries?size=101")
            .exchange()
            .expectStatus().isBadRequest

        verify(exactly = 0) { waitlistQueryTelemetry.recordListQuery(any(), any()) }
    }

    @Test
    fun `listEntries records telemetry with emailSearch true when email filter is provided`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.list(any()) } returns PagedResult.of(listOf(summary()), 0, 25, 1)

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries?email=candidate@example.com")
            .exchange()
            .expectStatus().isOk

        verify {
            waitlistQueryTelemetry.recordListQuery(
                statusFilterApplied = false,
                emailSearch = true,
            )
        }
    }

    @Test
    fun `listEntries records telemetry with emailSearch false when email filter is blank`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.list(any()) } returns PagedResult.of(listOf(summary()), 0, 25, 1)

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries?email=   ")
            .exchange()
            .expectStatus().isOk

        verify {
            waitlistQueryTelemetry.recordListQuery(
                statusFilterApplied = false,
                emailSearch = false,
            )
        }
    }

    @Test
    fun `listEntries does not record telemetry when unauthenticated`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/waitlist-entries")
            .exchange()
            .expectStatus().isUnauthorized

        verify(exactly = 0) { waitlistQueryTelemetry.recordListQuery(any(), any()) }
    }

    @Test
    fun `listEntries does not record telemetry when operator lacks read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries")
            .exchange()
            .expectStatus().isForbidden

        verify(exactly = 0) { waitlistQueryTelemetry.recordListQuery(any(), any()) }
    }

    @Test
    fun `getEntry returns detail for existing entry`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.findById(entryId) } returns detail()

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries/$entryId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(entryId)
            .jsonPath("$.status").isEqualTo("PENDING")
    }

    @Test
    fun `getEntry returns 404 when entry does not exist`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.findById(entryId) } returns null

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries/$entryId")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `getEntry returns 403 when operator lacks waitlist read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/waitlist-entries/$entryId")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `invite returns 201 with invitation summary`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { inviteHandler.handle(any()) } returns invitationSummary()

        webClient()
            .post()
            .uri("/api/admin/waitlist-entries/$entryId/invitations")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.waitlistEntryId").isEqualTo(entryId)
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    @Test
    fun `cancel returns 200 with cancelled status and delegates reason`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/waitlist-entries/$entryId/cancel")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reason":"spam"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("cancelled")

        coVerify { cancelHandler.handle(match { it.reason == "spam" }) }
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminWaitlistController(
                waitlistQuery = waitlistQuery,
                inviteHandler = inviteHandler,
                cancelHandler = cancelHandler,
                operatorAccessResolver = operatorAccessResolver,
                requestContextStore = FakeRequestContextStore(principal),
                waitlistQueryTelemetry = waitlistQueryTelemetry,
            ),
        )
        .controllerAdvice(AdminProblemDetailsHandler())
        .build()

    private fun grantRoles(roles: List<PlatformRole>) {
        coEvery { operatorAccessResolver.resolve(any()) } returns OperatorAccess(operatorId, roles.toSet())
    }

    private fun operatorPrincipal() = PrincipalContext(
        principalId = operatorId.toString(),
        principalType = PrincipalType.USER,
        subject = "operator@example.com",
        provider = "jwt",
    )

    private fun summary() = AdminWaitlistEntrySummary(
        id = entryId,
        waitlistId = "waitlist-1",
        waitlistKey = "profile-tailors-launch",
        email = "candidate@example.com",
        normalizedEmail = "candidate@example.com",
        status = "PENDING",
        joinedAt = clock.minusSeconds(3600),
        invitedAt = null,
        convertedAt = null,
        cancelledAt = null,
        preferredLocale = "en",
        source = "web",
    )

    private fun detail() = AdminWaitlistEntryDetail(
        id = entryId,
        waitlistId = "waitlist-1",
        waitlistKey = "profile-tailors-launch",
        email = "candidate@example.com",
        normalizedEmail = "candidate@example.com",
        status = "PENDING",
        joinedAt = clock.minusSeconds(3600),
        invitedAt = null,
        convertedAt = null,
        cancelledAt = null,
        preferredLocale = "en",
        earlyAccessConsent = true,
        marketingConsent = false,
        consentVersion = "1.0",
        source = "web",
        metadataSummary = emptyMap(),
        invitationHistory = emptyList(),
        version = 0,
    )

    private fun invitationSummary() = AdminInvitationSummary(
        id = UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
        waitlistEntryId = entryId,
        status = "ACTIVE",
        issuedAt = clock,
        expiresAt = clock.plusSeconds(604_800),
        acceptedAt = null,
        revokedAt = null,
        revokedBy = null,
        createdBy = operatorId,
        deliveryStatus = "PENDING",
        deliveryAttemptCount = 0,
        version = 0,
    )

    private class FakeRequestContextStore(private val principal: PrincipalContext?) : RequestContextStore {
        override fun currentPrincipalContext(): PrincipalContext? = principal
        override fun setPrincipalContext(context: PrincipalContext?) = Unit
        override fun currentResourceContext(): ResourceContext? = null
        override fun setResourceContext(context: ResourceContext?) = Unit
        override fun currentRequestPath(): String? = null
        override fun setRequestPath(path: String?) = Unit
        override fun clear() = Unit
    }
}
