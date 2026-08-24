package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.ports.AdminUserQuery
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminSessionControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val operatorAccessResolver = mockk<OperatorAccessResolver>()
    private val userQuery = mockk<AdminUserQuery>()

    @Test
    fun `getSession returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/session")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `getSession returns 403 when operator has no platform roles`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/session")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `getSession returns session with roles when operator has assignments`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userQuery.findById(operatorId.toString()) } returns userDetail()

        webClient()
            .get()
            .uri("/api/admin/session")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo(operatorId.toString())
            .jsonPath("$.email").isEqualTo("operator@example.com")
            .jsonPath("$.displayName").isEqualTo("Operator Example")
            .jsonPath("$.platformRoles[0]").isEqualTo("PLATFORM_OWNER")
    }

    @Test
    fun `getSession falls back to subject email when user detail is missing`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))
        coEvery { userQuery.findById(operatorId.toString()) } returns null

        webClient()
            .get()
            .uri("/api/admin/session")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo(operatorId.toString())
            .jsonPath("$.email").isEqualTo("operator@example.com")
            .jsonPath("$.displayName").doesNotExist()
            .jsonPath("$.platformRoles[0]").isEqualTo("PLATFORM_OPERATOR")
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminSessionController(
                operatorAccessResolver = operatorAccessResolver,
                userQuery = userQuery,
                requestContextStore = FakeRequestContextStore(principal),
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

    private fun userDetail() = AdminUserDetail(
        principalId = operatorId.toString(),
        email = "operator@example.com",
        displayIdentity = "Operator Example",
        principalType = "USER",
        createdAt = clock,
        lastAuthenticatedAt = clock,
        authenticationMethods = listOf("jwt"),
        workspaceMemberships = emptyList(),
        platformRoles = listOf("PLATFORM_OWNER"),
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
