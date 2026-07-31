package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.handler.AssignPlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokePlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminOperatorControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val targetId = UUID.fromString("00000000-0000-0000-0000-0000000000b1")

    private val roleAssignmentRepository = mockk<PlatformRoleAssignmentRepository>()
    private val assignRoleHandler = mockk<AssignPlatformRoleHandler>(relaxed = true)
    private val revokeRoleHandler = mockk<RevokePlatformRoleHandler>(relaxed = true)

    @Test
    fun `listOperators returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/operators")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `listOperators returns 403 when operator lacks operators read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/operators")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `listOperators groups active assignments by principal`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { roleAssignmentRepository.findAllActive() } returns listOf(
            assignment(operatorId, PlatformRole.PLATFORM_OWNER),
            assignment(targetId, PlatformRole.SUPPORT_AGENT),
            assignment(targetId, PlatformRole.AUDITOR),
        )

        webClient()
            .get()
            .uri("/api/admin/operators")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].principalId").isEqualTo(operatorId.toString())
            .jsonPath("$[0].platformRoles[0]").isEqualTo("PLATFORM_OWNER")
            .jsonPath("$[1].principalId").isEqualTo(targetId.toString())
            .jsonPath("$[1].platformRoles.length()").isEqualTo(2)
    }

    @Test
    fun `assignRole returns 200 with assigned status and delegates command`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/operators/$targetId/roles")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"role":"SUPPORT_AGENT"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("assigned")

        coVerify {
            assignRoleHandler.handle(
                match { it.targetPrincipalId == targetId && it.role == PlatformRole.SUPPORT_AGENT },
            )
        }
    }

    @Test
    fun `assignRole returns 400 for invalid role`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/operators/$targetId/roles")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"role":"NOT_A_ROLE"}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `revokeRole returns 200 with revoked status and delegates command`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .delete()
            .uri("/api/admin/operators/$targetId/roles/SUPPORT_AGENT")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("revoked")

        coVerify {
            revokeRoleHandler.handle(
                match { it.targetPrincipalId == targetId && it.role == PlatformRole.SUPPORT_AGENT },
            )
        }
    }

    @Test
    fun `revokeRole returns 400 for invalid role`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .delete()
            .uri("/api/admin/operators/$targetId/roles/NOT_A_ROLE")
            .exchange()
            .expectStatus().isBadRequest
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminOperatorController(
                roleAssignmentRepository = roleAssignmentRepository,
                assignRoleHandler = assignRoleHandler,
                revokeRoleHandler = revokeRoleHandler,
                requestContextStore = FakeRequestContextStore(principal),
            ),
        )
        .controllerAdvice(AdminProblemDetailsHandler())
        .build()

    private fun grantRoles(roles: List<PlatformRole>) {
        coEvery { roleAssignmentRepository.findActiveByPrincipalId(operatorId) } returns
            roles.map { assignment(operatorId, it) }
    }

    private fun operatorPrincipal() = PrincipalContext(
        principalId = operatorId.toString(),
        principalType = PrincipalType.USER,
        subject = "operator@example.com",
        provider = "jwt",
    )

    private fun assignment(principalId: UUID, role: PlatformRole) = PlatformRoleAssignment(
        id = PlatformRoleAssignmentId.generate(),
        principalId = principalId,
        role = role,
        assignedAt = clock.minusSeconds(3600),
        assignedBy = operatorId,
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
