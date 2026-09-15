package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.identity.application.PrincipalNotFoundException
import com.profiletailors.smp.identity.application.PrincipalVersionConflictException
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.contracts.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.handler.DeactivateUserHandler
import com.profiletailors.smp.platformadmin.application.handler.ReactivateUserHandler
import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminUserControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val userId = "00000000-0000-0000-0000-000000000011"

    private val userQuery = mockk<AdminUserQuery>()
    private val operatorAccessResolver = mockk<OperatorAccessResolver>()
    private val deactivateUserHandler = mockk<DeactivateUserHandler>()
    private val reactivateUserHandler = mockk<ReactivateUserHandler>()

    @Test
    fun `listUsers returns 401 without principal context`() {
        webClient(principal = null)
            .get()
            .uri("/api/admin/users")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `listUsers returns 403 when operator lacks users read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/users")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `listUsers returns users and forwards filters to query`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userQuery.list(any()) } returns PagedResult.of(listOf(summary()), 0, 25, 1)

        webClient()
            .get()
            .uri("/api/admin/users?status=ACTIVE&email=user@example.com")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].principalId").isEqualTo(userId)
            .jsonPath("$.items[0].email").isEqualTo("user@example.com")
            .jsonPath("$.totalElements").isEqualTo(1)

        coVerify {
            userQuery.list(
                match { query -> query.status == "ACTIVE" && query.email == "user@example.com" },
            )
        }
    }

    @Test
    fun `should return 400 when size exceeds max page size`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .get()
            .uri("/api/admin/users?size=101")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `getUser returns detail for existing user`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userQuery.findById(userId) } returns detail()

        webClient()
            .get()
            .uri("/api/admin/users/$userId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo(userId)
            .jsonPath("$.email").isEqualTo("user@example.com")
    }

    @Test
    fun `getUser returns 404 when user does not exist`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userQuery.findById(userId) } returns null

        webClient()
            .get()
            .uri("/api/admin/users/$userId")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `getUser returns 403 when operator lacks users read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/users/$userId")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `getUserWorkspaces returns memberships for existing user`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userQuery.findWorkspacesByPrincipalId(userId) } returns listOf(workspace())

        webClient()
            .get()
            .uri("/api/admin/users/$userId/workspaces")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].workspaceId").isEqualTo("workspace-1")
            .jsonPath("$[0].membershipStatus").isEqualTo("ACTIVE")
    }

    @Test
    fun `deactivateUser returns 204 on successful transition`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { deactivateUserHandler.handle(any()) } returns Unit

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/deactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `deactivateUser returns 401 without principal context`() {
        webClient(principal = null)
            .patch()
            .uri("/api/admin/users/$userId/deactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `deactivateUser returns 403 when operator lacks deactivate permission`() {
        grantRoles(emptyList())
        coEvery { deactivateUserHandler.handle(any()) } throws
            PlatformAccessDeniedException(PlatformPermission.USERS_DEACTIVATE)

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/deactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `deactivateUser returns 404 when principal does not exist`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { deactivateUserHandler.handle(any()) } throws PrincipalNotFoundException(userId)

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/deactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.code").isEqualTo("USER_PRINCIPAL_NOT_FOUND")
    }

    @Test
    fun `deactivateUser returns 409 on version conflict`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { deactivateUserHandler.handle(any()) } throws PrincipalVersionConflictException(userId, 1, 2)

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/deactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("USER_ACCOUNT_VERSION_CONFLICT")
    }

    @Test
    fun `reactivateUser returns 204 on successful transition`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { reactivateUserHandler.handle(any()) } returns Unit

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/reactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `reactivateUser returns 401 without principal context`() {
        webClient(principal = null)
            .patch()
            .uri("/api/admin/users/$userId/reactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `reactivateUser returns 403 when operator lacks reactivate permission`() {
        grantRoles(emptyList())
        coEvery { reactivateUserHandler.handle(any()) } throws
            PlatformAccessDeniedException(PlatformPermission.USERS_REACTIVATE)

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/reactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    @Test
    fun `reactivateUser returns 404 when principal does not exist`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { reactivateUserHandler.handle(any()) } throws PrincipalNotFoundException(userId)

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/reactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.code").isEqualTo("USER_PRINCIPAL_NOT_FOUND")
    }

    @Test
    fun `reactivateUser returns 409 on version conflict`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { reactivateUserHandler.handle(any()) } throws PrincipalVersionConflictException(userId, 1, 2)

        webClient()
            .patch()
            .uri("/api/admin/users/$userId/reactivate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expectedVersion":1}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("USER_ACCOUNT_VERSION_CONFLICT")
    }

    @Test
    fun `getUserWorkspaces returns 403 when operator lacks workspaces read permission`() {
        grantRoles(emptyList())

        webClient()
            .get()
            .uri("/api/admin/users/$userId/workspaces")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("PLATFORM_ACCESS_DENIED")
    }

    private fun webClient(principal: PrincipalContext? = operatorPrincipal()): WebTestClient = WebTestClient
        .bindToController(
            AdminUserController(
                userQuery = userQuery,
                operatorAccessResolver = operatorAccessResolver,
                requestContextStore = FakeRequestContextStore(principal),
                deactivateUserHandler = deactivateUserHandler,
                reactivateUserHandler = reactivateUserHandler,
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

    private fun summary() = AdminUserSummary(
        principalId = userId,
        email = "user@example.com",
        displayIdentity = "User Example",
        principalType = "USER",
        createdAt = clock,
        lastAuthenticatedAt = clock,
        authenticationMethods = listOf("jwt"),
        workspaceCount = 1,
        platformRoles = listOf("PLATFORM_OWNER"),
    )

    private fun detail() = AdminUserDetail(
        principalId = userId,
        email = "user@example.com",
        displayIdentity = "User Example",
        principalType = "USER",
        createdAt = clock,
        lastAuthenticatedAt = clock,
        authenticationMethods = listOf("jwt"),
        workspaceMemberships = listOf(workspace()),
        platformRoles = listOf("PLATFORM_OWNER"),
    )

    private fun workspace() = AdminWorkspaceMembershipSummary(
        workspaceId = "workspace-1",
        workspaceName = "Example Workspace",
        membershipStatus = "ACTIVE",
        workspaceRoles = listOf("OWNER"),
        joinedAt = clock,
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
