package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.platformadmin.infrastructure.http.AdminProblemDetailsHandler
import com.profiletailors.smp.publishing.application.ListStaleJobsQuery
import com.profiletailors.smp.publishing.application.StaleJobItem
import com.profiletailors.smp.publishing.application.StaleJobsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class PublishingStaleJobsControllerTest {

    private val operatorId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000001")
    private val fixedClock = Instant.parse("2026-08-22T12:00:00Z")

    private val mediator = mockk<Mediator>()
    private val roleAssignmentRepository = mockk<PlatformRoleAssignmentRepository>()
    private val defaultPrincipal = operatorPrincipal()

    @Test
    fun `returns 200 with stale jobs body when operator has PUBLISHING_STALE_READ`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))
        coEvery { mediator.send(any<ListStaleJobsQuery>()) } returns sampleResponse()

        webClient(defaultPrincipal)
            .get()
            .uri("/api/admin/publishing/stale-jobs?leaseStaleThreshold=PT5M&limit=50")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.total").isEqualTo(1)
            .jsonPath("$.staleJobs[0].jobId").isEqualTo("pjob-1")
            .jsonPath("$.staleJobs[0].publicationId").isEqualTo("pub-stuck-1")
            .jsonPath("$.staleJobs[0].workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.staleJobs[0].claimedByWorker").isEqualTo("worker-stuck-uuid")
            .jsonPath("$.staleJobs[0].attemptNumber").isEqualTo(4)
            .jsonPath("$.staleJobs[0].suggestedAction").isEqualTo("RELEASE_AND_RETRY")
            .jsonPath("$.staleJobs[0].ageSeconds").isEqualTo(900)

        coVerify {
            mediator.send(
                match<ListStaleJobsQuery> {
                    it.leaseStaleThreshold == java.time.Duration.ofMinutes(5) && it.limit == 50
                },
            )
        }
    }

    @Test
    fun `returns 401 when no principal context is available`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))

        webClient(principal = null)
            .get()
            .uri("/api/admin/publishing/stale-jobs")
            .exchange()
            .expectStatus().isUnauthorized

        coVerify(exactly = 0) { mediator.send(any<ListStaleJobsQuery>()) }
    }

    @Test
    fun `returns 403 when operator lacks PUBLISHING_STALE_READ permission`() {
        grantRoles(listOf(PlatformRole.AUDITOR))

        webClient(defaultPrincipal)
            .get()
            .uri("/api/admin/publishing/stale-jobs")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")

        coVerify(exactly = 0) { mediator.send(any<ListStaleJobsQuery>()) }
    }

    @Test
    fun `returns 400 when threshold format is invalid`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))

        webClient(defaultPrincipal)
            .get()
            .uri("/api/admin/publishing/stale-jobs?leaseStaleThreshold=not-a-duration")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")

        coVerify(exactly = 0) { mediator.send(any<ListStaleJobsQuery>()) }
    }

    @Test
    fun `returns 400 without dispatching when threshold is non-positive`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))

        webClient(defaultPrincipal)
            .get()
            .uri("/api/admin/publishing/stale-jobs?leaseStaleThreshold=PT0S")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")

        coVerify(exactly = 0) { mediator.send(any<ListStaleJobsQuery>()) }
    }

    @Test
    fun `returns 400 without dispatching when limit is below the lower bound`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))

        webClient(defaultPrincipal)
            .get()
            .uri("/api/admin/publishing/stale-jobs?limit=0")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")

        coVerify(exactly = 0) { mediator.send(any<ListStaleJobsQuery>()) }
    }

    @Test
    fun `returns 400 without dispatching when limit exceeds the upper bound`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))

        webClient(defaultPrincipal)
            .get()
            .uri("/api/admin/publishing/stale-jobs?limit=101")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")

        coVerify(exactly = 0) { mediator.send(any<ListStaleJobsQuery>()) }
    }

    @Test
    fun `success response exposes only the safe stale job contract`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))
        coEvery { mediator.send(any<ListStaleJobsQuery>()) } returns sampleResponse()

        val body = webClient(defaultPrincipal)
            .get()
            .uri("/api/admin/publishing/stale-jobs")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody
            .orEmpty()

        assertTrue(body.contains("\"jobId\""))
        assertTrue(body.contains(""""suggestedAction":"RELEASE_AND_RETRY"""))
        assertFalse(body.contains("providerPayload"))
        assertFalse(body.contains("credentials"))
        assertFalse(body.contains("storagePath"))
        assertFalse(body.contains("token="))
        assertFalse(body.contains("Bearer "))
        assertFalse(body.contains("https://"))
        assertFalse(body.contains("Exception"))
        assertFalse(body.contains("Error"))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun webClient(principal: PrincipalContext? = defaultPrincipal): WebTestClient =
        WebTestClient.bindToController(
            PublishingStaleJobsController(
                mediator = mediator,
                roleAssignmentRepository = roleAssignmentRepository,
                requestContextStore = FakeRequestContextStore(principal),
            ),
        )
            .controllerAdvice(AdminProblemDetailsHandler())
            .build()

    private fun grantRoles(roles: List<PlatformRole>) {
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(operatorId) } returns
            roles.map { assignment(it) }
    }

    private fun operatorPrincipal() = PrincipalContext(
        principalId = operatorId.toString(),
        principalType = PrincipalType.USER,
        subject = "admin@platform.example",
        provider = "https://issuer.example",
    )

    private fun assignment(role: PlatformRole) = PlatformRoleAssignment(
        id = PlatformRoleAssignmentId.generate(),
        principalId = operatorId,
        role = role,
        assignedAt = fixedClock,
        assignedBy = operatorId,
    )

    private fun sampleResponse(): StaleJobsResponse = StaleJobsResponse(
        staleJobs = listOf(
            StaleJobItem(
                jobId = "pjob-1",
                publicationId = "pub-stuck-1",
                workspaceId = "workspace-1",
                claimedByWorker = "worker-stuck-uuid",
                claimedAt = Instant.parse("2026-08-22T11:45:00Z"),
                leaseExpiresAt = Instant.parse("2026-08-22T11:48:00Z"),
                ageSeconds = 900,
                attemptNumber = 4,
                suggestedAction = "RELEASE_AND_RETRY",
            ),
        ),
        total = 1,
    )

    /**
     * Regression check: the new platform permission is correctly wired into the
     * role matrix so the 200 / 403 split stays deterministic.
     */
    @Test
    fun `PUBLISHING_STALE_READ permission is granted to PLATFORM_OWNER and PLATFORM_OPERATOR but not AUDITOR`() {
        val operatorPermissions = setOf(PlatformRole.PLATFORM_OPERATOR).effectivePermissions()
        assertTrue(
            PlatformPermission.PUBLISHING_STALE_READ in operatorPermissions,
            "PLATFORM_OPERATOR must include PUBLISHING_STALE_READ for the new endpoint",
        )

        val ownerPermissions = setOf(PlatformRole.PLATFORM_OWNER).effectivePermissions()
        assertTrue(
            PlatformPermission.PUBLISHING_STALE_READ in ownerPermissions,
            "PLATFORM_OWNER must include PUBLISHING_STALE_READ",
        )

        val auditorPermissions = setOf(PlatformRole.AUDITOR).effectivePermissions()
        assertFalse(
            PlatformPermission.PUBLISHING_STALE_READ in auditorPermissions,
            "AUDITOR must NOT include PUBLISHING_STALE_READ (used to verify the 403 path)",
        )
    }

    // Test infrastructure
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
