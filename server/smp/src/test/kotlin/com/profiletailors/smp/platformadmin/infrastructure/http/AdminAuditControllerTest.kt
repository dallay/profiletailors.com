package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.model.AdminAuditEventSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.ports.AdminAuditQuery
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminAuditControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val eventId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")

    private val auditQuery = mockk<AdminAuditQuery>()
    private val roleAssignmentRepository = mockk<PlatformRoleAssignmentRepository>()

    @Test
    fun `listEvents returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/audit-events")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `listEvents returns 403 when operator lacks audit read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/audit-events")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `listEvents returns events and forwards filters to query`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { auditQuery.list(any()) } returns PagedResult.of(listOf(event()), 0, 25, 1)

        webClient()
            .get()
            .uri("/api/admin/audit-events?action=INVITATION_REVOKED&result=SUCCEEDED")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].eventId").isEqualTo(eventId.toString())
            .jsonPath("$.items[0].action").isEqualTo("INVITATION_REVOKED")
            .jsonPath("$.totalElements").isEqualTo(1)

        coVerify {
            auditQuery.list(
                match { query -> query.action == "INVITATION_REVOKED" && query.result == "SUCCEEDED" },
            )
        }
    }

    @Test
    fun `listEvents returns 400 when size exceeds max page size`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .get()
            .uri("/api/admin/audit-events?size=101")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `getEvent returns detail for existing event`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { auditQuery.findById(eventId) } returns event()

        webClient()
            .get()
            .uri("/api/admin/audit-events/$eventId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.eventId").isEqualTo(eventId.toString())
            .jsonPath("$.action").isEqualTo("INVITATION_REVOKED")
    }

    @Test
    fun `getEvent returns 404 when event does not exist`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { auditQuery.findById(eventId) } returns null

        webClient()
            .get()
            .uri("/api/admin/audit-events/$eventId")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `getEvent returns 403 when operator lacks audit read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/audit-events/$eventId")
            .exchange()
            .expectStatus().isForbidden
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminAuditController(
                auditQuery = auditQuery,
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
        subject = "operator@example.com",
        provider = "jwt",
    )

    private fun assignment(role: PlatformRole) = PlatformRoleAssignment(
        id = PlatformRoleAssignmentId.generate(),
        principalId = operatorId,
        role = role,
        assignedAt = clock,
        assignedBy = operatorId,
    )

    private fun event() = AdminAuditEventSummary(
        eventId = eventId,
        occurredAt = clock,
        operatorPrincipalId = operatorId,
        operatorPlatformRoles = listOf("PLATFORM_OWNER"),
        action = "INVITATION_REVOKED",
        targetType = "WaitlistInvitation",
        targetId = "00000000-0000-0000-0000-0000000000a1",
        result = "SUCCEEDED",
        reason = null,
        correlationId = "corr-1",
        requestId = "req-1",
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
