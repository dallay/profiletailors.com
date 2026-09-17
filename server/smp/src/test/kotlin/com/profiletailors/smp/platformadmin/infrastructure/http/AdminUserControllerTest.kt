package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyService
import com.profiletailors.smp.platformadmin.application.contracts.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.handler.UserControlHandlers
import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.model.UserControlResult
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

class AdminUserControllerTest {

    private val clock = Instant.parse("2026-07-30T10:00:00Z")
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val userId = "00000000-0000-0000-0000-000000000011"

    private val userQuery = mockk<AdminUserQuery>()
    private val operatorAccessResolver = mockk<OperatorAccessResolver>()
    private val userControlHandlers = mockk<UserControlHandlers>()
    private val userControlIdempotencyService = mockk<UserControlIdempotencyService>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>()
    private val auditHook = mockk<com.profiletailors.smp.audit.domain.AuditHook>(relaxed = true)

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
    fun `disableUser forwards idempotency key and returns control result`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userControlHandlers.disable(any()) } returns UserControlResult(userId, UserAccountState.DISABLED, 2)
        coEvery {
            userControlIdempotencyService.execute(
                any(),
                "disable",
                userId,
                "disable-key",
                UserControlResult::class.java,
                any<suspend () -> UserControlResult>(),
            )
        } returns UserControlResult(userId, UserAccountState.DISABLED, 2)

        webClient()
            .post()
            .uri("/api/admin/users/$userId/disable")
            .header("Idempotency-Key", "disable-key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo(userId)
            .jsonPath("$.accountState").isEqualTo("DISABLED")
            .jsonPath("$.revokedSessionCount").isEqualTo(2)
    }

    @Test
    fun `disableUser returns 403 when operator lacks manage permission`() {
        grantRoles(listOf(PlatformRole.SUPPORT_AGENT))

        webClient()
            .post()
            .uri("/api/admin/users/$userId/disable")
            .header("Idempotency-Key", "disable-key")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `disableUser returns 401 without principal context`() {
        webClient(principal = null)
            .post()
            .uri("/api/admin/users/$userId/disable")
            .header("Idempotency-Key", "disable-key")
            .exchange()
            .expectStatus().isUnauthorized

        coVerify {
            auditHook.onMutation(
                match { fact ->
                    fact.action == "USER_DISABLED" &&
                        fact.actorPrincipalId == "UNAUTHENTICATED" &&
                        fact.targetId == userId &&
                        fact.outcome == com.profiletailors.smp.audit.domain.MutationAuditOutcome.REJECTED
                },
            )
        }
    }

    @Test
    fun `revokeUserSessions requires idempotency key`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))

        webClient()
            .post()
            .uri("/api/admin/users/$userId/sessions/revoke")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `getUser returns user detail`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userQuery.findById(userId) } returns detail()

        webClient()
            .get()
            .uri("/api/admin/users/$userId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceMemberships[0].workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.platformRoles[0]").isEqualTo("PLATFORM_OWNER")
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
        grantRoles(listOf(PlatformRole.PLATFORM_OPERATOR))
        coEvery { userQuery.findById(userId) } returns detail()
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

    @Test
    fun `getUserWorkspaces requires users read permission as well as workspace read`() {
        grantRoles(listOf(PlatformRole.AUDITOR))

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
                auditHook = auditHook,
                userControlHandlers = userControlHandlers,
                userControlIdempotencyService = userControlIdempotencyService,

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
        accountState = UserAccountState.ACTIVE,
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
        accountState = UserAccountState.ACTIVE,
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
