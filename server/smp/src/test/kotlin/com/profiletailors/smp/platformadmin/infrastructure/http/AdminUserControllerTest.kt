package com.profiletailors.smp.platformadmin.infrastructure.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.ExternalIdentityLinkConflictException
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.ProvisionExternalIdentityLinkHandler
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyCodec
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyRecord
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyService
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyStore
import com.profiletailors.smp.platformadmin.application.contracts.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.handler.UserControlHandlers
import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.model.UserControlResult
import com.profiletailors.smp.platformadmin.application.model.UserSessionsRevokeResult
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
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
    private val userControlIdempotencyStore = FakeUserControlIdempotencyStore()
    private val userControlIdempotencyService =
        UserControlIdempotencyService(userControlIdempotencyStore, FakeUserControlIdempotencyCodec())
    private val auditPublisher = mockk<AdministrativeAuditPublisher>()
    private val auditHook = mockk<com.profiletailors.smp.audit.domain.AuditHook>(relaxed = true)
    private val identityLinkHandler = mockk<ProvisionExternalIdentityLinkHandler>(relaxed = true)

    @BeforeEach
    fun resetIdempotencyStore() {
        userControlIdempotencyStore.clear()
    }

    @Test
    fun `identity link endpoint requires idempotency key and returns created`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        webClient()
            .post()
            .uri("/api/admin/users/$userId/identity-link")
            .header("Idempotency-Key", "link-key")
            .bodyValue(mapOf("issuer" to "https://keycloak.ahome.quest/realms/pt", "subject" to "subject-1"))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.principalId").isEqualTo(userId)

        coVerify { identityLinkHandler.handle(match { it.targetPrincipalId == userId && it.subject == "subject-1" }) }
        assertEquals(userId, userControlIdempotencyStore.lastClaimedRecord?.targetPrincipalId)
    }

    @Test
    fun `identity link endpoint maps changed identity for reused idempotency key to safe problem details`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        identityLinkRequest("safe-conflict-key", "https://keycloak.ahome.quest/realms/pt", "subject-1")
            .expectStatus().isCreated

        identityLinkRequest("safe-conflict-key", "https://keycloak.ahome.quest/realms/pt", "subject-2")
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED")
            .jsonPath("$.detail").isEqualTo("Idempotency key was already used for another user control request.")

        coVerify(exactly = 1) { identityLinkHandler.handle(any()) }
    }

    @Test
    fun `identity link endpoint replays identical request without invoking handler twice`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        val body = mapOf("issuer" to "https://keycloak.ahome.quest/realms/pt", "subject" to "subject-1")

        repeat(2) {
            webClient()
                .post()
                .uri("/api/admin/users/$userId/identity-link")
                .header("Idempotency-Key", "link-replay-key")
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.principalId").isEqualTo(userId)
        }

        coVerify(exactly = 1) { identityLinkHandler.handle(any()) }
    }

    @Test
    fun `identity link endpoint rejects changed subject for a reused key`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        identityLinkRequest("subject-conflict-key", "https://keycloak.ahome.quest/realms/pt", "subject-1")
            .expectStatus().isCreated
        identityLinkRequest("subject-conflict-key", "https://keycloak.ahome.quest/realms/pt", "subject-2")
            .expectStatus().isEqualTo(409)

        coVerify(exactly = 1) { identityLinkHandler.handle(any()) }
    }

    @Test
    fun `identity link endpoint rejects changed issuer for a reused key`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        identityLinkRequest("issuer-conflict-key", "https://keycloak.ahome.quest/realms/pt", "subject-1")
            .expectStatus().isCreated
        identityLinkRequest("issuer-conflict-key", "https://keycloak.example/realms/pt", "subject-1")
            .expectStatus().isEqualTo(409)

        coVerify(exactly = 1) { identityLinkHandler.handle(any()) }
    }

    @Test
    fun `identity link endpoint checks manage permission before replaying cached result`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        identityLinkRequest("permission-replay-key", "https://keycloak.ahome.quest/realms/pt", "subject-1")
            .expectStatus().isCreated

        grantRoles(listOf(PlatformRole.SUPPORT_AGENT))
        identityLinkRequest("permission-replay-key", "https://keycloak.ahome.quest/realms/pt", "subject-1")
            .expectStatus().isForbidden

        coVerify(exactly = 1) { identityLinkHandler.handle(any()) }
    }

    private fun identityLinkRequest(key: String, issuer: String, subject: String) = webClient()
        .post()
        .uri("/api/admin/users/$userId/identity-link")
        .header("Idempotency-Key", key)
        .bodyValue(mapOf("issuer" to issuer, "subject" to subject))
        .exchange()

    @Test
    fun `identity link conflict response omits the owner principal id and stays generic`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        val firstOwnerPrincipalId = "00000000-0000-0000-0000-0000000000a1"
        val secondOwnerPrincipalId = "00000000-0000-0000-0000-0000000000b2"

        coEvery { identityLinkHandler.handle(any()) } throws ExternalIdentityLinkConflictException(
            DuplicateKeyException(
                "duplicate key value violates unique constraint pk_external_identity_links " +
                    "owner principal $firstOwnerPrincipalId",
            ),
        )
        val firstBody = identityLinkRequest(
            "conflict-owner-key-one",
            "https://keycloak.ahome.quest/realms/pt",
            "subject-1",
        )
            .expectStatus().isEqualTo(409)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        coEvery { identityLinkHandler.handle(any()) } throws ExternalIdentityLinkConflictException(
            DuplicateKeyException(
                "duplicate key value violates unique constraint pk_external_identity_links " +
                    "owner principal $secondOwnerPrincipalId",
            ),
        )
        val secondBody = identityLinkRequest(
            "conflict-owner-key-two",
            "https://keycloak.ahome.quest/realms/pt",
            "subject-1",
        )
            .expectStatus().isEqualTo(409)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val document = jacksonObjectMapper().readTree(requireNotNull(firstBody))
        assertEquals("IDENTITY_LINK_CONFLICT", document.path("code").asText())
        assertEquals("Identity link already exists.", document.path("detail").asText())
        assertEquals("urn:profiletailors:error:IDENTITY_LINK_CONFLICT", document.path("type").asText())
        assertEquals(409, document.path("status").asInt())
        assertFalse(requireNotNull(firstBody).contains(firstOwnerPrincipalId))
        assertFalse(requireNotNull(firstBody).contains(secondOwnerPrincipalId))
        assertFalse(requireNotNull(secondBody).contains(firstOwnerPrincipalId))
        assertFalse(requireNotNull(secondBody).contains(secondOwnerPrincipalId))
        assertEquals(firstBody, secondBody)
    }

    @Test
    fun `identity link endpoint rejects missing idempotency key without disclosing internals`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        val body = webClient()
            .post()
            .uri("/api/admin/users/$userId/identity-link")
            .bodyValue(mapOf("issuer" to "https://keycloak.ahome.quest/realms/pt", "subject" to "subject-1"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertTrue(body.isNullOrBlank())
    }

    @Test
    fun `identity link endpoint rejects an invalid idempotency key with a problem detail body`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))

        val body = webClient()
            .post()
            .uri("/api/admin/users/$userId/identity-link")
            .header("Idempotency-Key", "")
            .bodyValue(mapOf("issuer" to "https://keycloak.ahome.quest/realms/pt", "subject" to "subject-1"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val document = jacksonObjectMapper().readTree(requireNotNull(body))
        assertEquals("VALIDATION_ERROR", document.path("code").asText())
        assertEquals("urn:profiletailors:error:VALIDATION_ERROR", document.path("type").asText())
        assertEquals(400, document.path("status").asInt())
        assertEquals("Bad Request", document.path("title").asText())
    }

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
    fun `should enable the user when the idempotency key is valid`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userControlHandlers.enable(any()) } returns UserControlResult(userId, UserAccountState.ACTIVE, 0)

        webClient()
            .post()
            .uri("/api/admin/users/$userId/enable")
            .header("Idempotency-Key", "enable-key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo(userId)
            .jsonPath("$.accountState").isEqualTo("ACTIVE")
            .jsonPath("$.revokedSessionCount").isEqualTo(0)
    }

    @Test
    fun `should reject enable with 401 when unauthenticated`() {
        webClient(principal = null)
            .post()
            .uri("/api/admin/users/$userId/enable")
            .header("Idempotency-Key", "enable-key")
            .exchange()
            .expectStatus().isUnauthorized

        coVerify {
            auditHook.onMutation(
                match { fact ->
                    fact.action == "USER_ENABLED" &&
                        fact.actorPrincipalId == "UNAUTHENTICATED" &&
                        fact.targetId == userId &&
                        fact.outcome == com.profiletailors.smp.audit.domain.MutationAuditOutcome.REJECTED
                },
            )
        }
    }

    @Test
    fun `should revoke sessions when the idempotency key is valid`() {
        grantRoles(listOf(PlatformRole.PLATFORM_OWNER))
        coEvery { userControlHandlers.revokeSessions(any()) } returns UserSessionsRevokeResult(userId, 2)

        webClient()
            .post()
            .uri("/api/admin/users/$userId/sessions/revoke")
            .header("Idempotency-Key", "revoke-key")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo(userId)
            .jsonPath("$.revokedSessionCount").isEqualTo(2)
    }

    @Test
    fun `should reject session revocation with 401 when unauthenticated`() {
        webClient(principal = null)
            .post()
            .uri("/api/admin/users/$userId/sessions/revoke")
            .header("Idempotency-Key", "revoke-key")
            .exchange()
            .expectStatus().isUnauthorized

        coVerify {
            auditHook.onMutation(
                match { fact ->
                    fact.action == "USER_SESSIONS_REVOKED" &&
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
                principalContextProvider = FakePrincipalContextProvider(principal),
                auditHook = auditHook,
                userControlHandlers = userControlHandlers,
                userControlIdempotencyService = userControlIdempotencyService,
                identityLinkHandler = identityLinkHandler,

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

    private class FakeUserControlIdempotencyStore : UserControlIdempotencyStore {
        private val records = mutableMapOf<Pair<UUID, String>, UserControlIdempotencyRecord>()
        var lastClaimedRecord: UserControlIdempotencyRecord? = null
            private set

        fun clear() {
            records.clear()
            lastClaimedRecord = null
        }

        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): UserControlIdempotencyRecord? =
            records[operatorPrincipalId to idempotencyKey]

        override suspend fun claim(record: UserControlIdempotencyRecord): Boolean {
            lastClaimedRecord = record
            if (record.operation != "identity_link") return true
            val key = record.operatorPrincipalId to record.idempotencyKey
            if (key in records) return false
            records[key] = record
            return true
        }

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) {
            val key = operatorPrincipalId to idempotencyKey
            val record = records[key] ?: return
            records[key] = record.copy(responseJson = responseJson)
        }

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) {
            records.remove(operatorPrincipalId to idempotencyKey)
        }
    }

    private class FakeUserControlIdempotencyCodec : UserControlIdempotencyCodec {
        private val values = mutableMapOf<String, Any>()

        override fun encode(value: Any): String {
            val key = UUID.randomUUID().toString()
            values[key] = value
            return key
        }

        override fun <T : Any> decode(responseJson: String, responseType: Class<T>): T =
            responseType.cast(values.getValue(responseJson))
    }

    private class FakePrincipalContextProvider(private val principal: PrincipalContext?) : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext? = principal
    }
}
