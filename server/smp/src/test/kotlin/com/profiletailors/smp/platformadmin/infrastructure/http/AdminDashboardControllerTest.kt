package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.contracts.AdminWaitlistQuery
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AdminDashboardControllerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val waitlistQuery = mockk<AdminWaitlistQuery>()
    private val operatorAccessResolver = mockk<OperatorAccessResolver>()

    @Test
    fun `getDashboard returns 401 without principal context`() {
        val client = WebTestClient
            .bindToController(
                AdminDashboardController(
                    waitlistQuery = waitlistQuery,
                    operatorAccessResolver = operatorAccessResolver,
                    requestContextStore = FakeRequestContextStore(null),
                    clock = clock,
                ),
            )
            .controllerAdvice(AdminProblemDetailsHandler())
            .build()

        client
            .get()
            .uri("/api/admin/dashboard")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `getDashboard returns 403 when operator lacks dashboard read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/dashboard")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `getDashboard returns summary with counts and default period`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.countByStatus() } returns
            mapOf(
                "PENDING" to 5L,
                "INVITED" to 3L,
                "CONVERTED" to 2L,
                "CANCELLED" to 1L,
            )

        webClient()
            .get()
            .uri("/api/admin/dashboard")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.pendingCount").isEqualTo(5)
            .jsonPath("$.invitedCount").isEqualTo(3)
            .jsonPath("$.convertedCount").isEqualTo(2)
            .jsonPath("$.cancelledCount").isEqualTo(1)
            .jsonPath("$.periodDays").isEqualTo(30)

        coVerify { waitlistQuery.countByStatus() }
    }

    @Test
    fun `getDashboard forwards periodDays parameter`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { waitlistQuery.countByStatus() } returns emptyMap()

        webClient()
            .get()
            .uri("/api/admin/dashboard?periodDays=7")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.periodDays").isEqualTo(7)
            .jsonPath("$.pendingCount").isEqualTo(0)
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminDashboardController(
                waitlistQuery = waitlistQuery,
                operatorAccessResolver = operatorAccessResolver,
                requestContextStore = FakeRequestContextStore(principal),
                clock = clock,
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
