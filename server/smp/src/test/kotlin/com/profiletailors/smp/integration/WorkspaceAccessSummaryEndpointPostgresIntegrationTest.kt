package com.profiletailors.smp.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialCommand
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialHandler
import com.profiletailors.smp.integration.support.CapturingAuditHook
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import kotlinx.coroutines.reactor.awaitSingle
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Instant

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Suppress("LargeClass")
class WorkspaceAccessSummaryEndpointPostgresIntegrationTest(
    @Autowired private val webTestClient: WebTestClient,
    @Autowired private val databaseClient: DatabaseClient,
    @Autowired private val auditHook: CapturingAuditHook,
    @Autowired private val replaceApiKeyCredentialHandler: ReplaceApiKeyCredentialHandler,
) {

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        auditHook.reset()
        kotlinx.coroutines.runBlocking {
            listOf(
                "DELETE FROM workspace_target_scopes",
                "DELETE FROM workspace_direct_grants",
                "DELETE FROM workspace_entitlements",
                "DELETE FROM membership_roles",
                "DELETE FROM role_permissions",
                "DELETE FROM roles",
                "DELETE FROM permissions",
                "DELETE FROM workspace_memberships",
                "DELETE FROM workspace_ownerships",
                "DELETE FROM workspaces",
                "DELETE FROM api_key_credentials",
                "DELETE FROM service_account_credentials",
                "DELETE FROM user_identities",
                "DELETE FROM principals",
            ).forEach { statement ->
                databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
            }
        }
    }

    @Test
    fun `returns workspace access summary for entitled authorized member on postgres`() {
        seedAuthorizedMember(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("principal-1")
            .jsonPath("$.roles[0]").isEqualTo("member")
            .jsonPath("$.permissions[0]").isEqualTo("workspace:access:read")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `denies entitled member without required permission on postgres`() {
        seedMemberWithoutPermission(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `workspace access summary remains unaffected by target scopes on postgres`() {
        seedAuthorizedMember(entitled = true)
        seedTargetScope(allowedTargetIdsJson = "[\"resource-99\"]")

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("principal-1")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `allows entitled member through persisted direct allow grant on postgres`() {
        seedMemberWithoutPermission(entitled = true)
        seedDirectGrant(effect = "ALLOW", permissionId = "permission-1", permissionKey = "workspace:access:read")

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("principal-1")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW,
                    reasonCode = AuthorizationReasonCode.DIRECT_ALLOW,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `direct deny overrides entitled role based allow on postgres`() {
        seedAuthorizedMember(entitled = true)
        seedDirectGrant(
            effect = "DENY",
            permissionId = "permission-1",
            permissionKey = "workspace:access:read",
            insertPermission = false,
        )

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.DIRECT_DENY,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `expired direct grant is ignored on entitled postgres member`() {
        seedMemberWithoutPermission(entitled = true)
        seedDirectGrant(
            effect = "ALLOW",
            permissionId = "permission-1",
            permissionKey = "workspace:access:read",
            expiresAt = "2026-05-15T09:00:00Z",
        )

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `returns workspace access summary for entitled authorized service account on postgres`() {
        seedAuthorizedServiceAccount(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("service-principal-1")
            .jsonPath("$.roles[0]").isEqualTo("member")
            .jsonPath("$.permissions[0]").isEqualTo("workspace:access:read")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "service-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `denies entitled service account without required permission on postgres`() {
        seedServiceAccountWithoutPermission(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "service-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `rejects revoked service-account credential before authorization executes on postgres`() {
        seedAuthorizedServiceAccount(credentialStatus = "REVOKED", entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "service-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL,
                    roleKeys = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `returns workspace access summary for entitled authorized api key principal on postgres`() {
        seedAuthorizedApiKeyPrincipal(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("api-key-principal-1")
            .jsonPath("$.roles[0]").isEqualTo("member")
            .jsonPath("$.permissions[0]").isEqualTo("workspace:access:read")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.ALLOW,
                    reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `denies entitled api key principal without required permission on postgres`() {
        seedApiKeyPrincipalWithoutPermission(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `rejects revoked api key credential before authorization executes on postgres`() {
        seedAuthorizedApiKeyPrincipal(credentialStatus = "REVOKED", entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL,
                    roleKeys = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `rejects inactive api key credential before authorization executes on postgres`() {
        seedAuthorizedApiKeyPrincipal(credentialStatus = "INACTIVE", entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "api-key-principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL,
                    roleKeys = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `denies authorized principal when workspace entitlement is missing on postgres`() {
        seedAuthorizedMember(entitled = false)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery",
                    requestPath = "/api/authorization/workspace-access/current",
                    permission = "workspace:access:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.MISSING_ENTITLEMENT,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `allows old api key before replacement and new api key after replacement on postgres`() {
        seedAuthorizedApiKeyPrincipal(entitled = true)

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo("api-key-principal-1")

        val replacement = kotlinx.coroutines.runBlocking {
            replaceApiKeyCredentialHandler.handle(
                ReplaceApiKeyCredentialCommand("api-key-cred-1"),
            )
        }

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${replacement.successorPlaintextApiKey}")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.principalId").isEqualTo("api-key-principal-1")

        val rows = kotlinx.coroutines.runBlocking { readApiKeyRows() }
        assertEquals(2, rows.size)
        val predecessor = rows.first { it.id == "api-key-cred-1" }
        val successor = rows.first { it.id == replacement.successorCredentialReference }
        assertEquals("INACTIVE", predecessor.status)
        assertEquals(replacement.successorCredentialReference, predecessor.replacedByCredentialId)
        assertEquals("ACTIVE", successor.status)
        assertEquals("api-key-cred-1", successor.replacedCredentialId)
    }

    private fun assertAuthorizationFacts(expected: List<AuthorizationDecisionAuditFact>) {
        assertEquals(expected, auditHook.facts)
    }

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).update(Contexts(), LabelExpression())
        }
    }

    private fun seedAuthorizedMember(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('principal-1', 'yuniel@example.com', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "principal-1", principalType = "USER", entitled = entitled)
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
        }
    }

    private fun seedMemberWithoutPermission(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('principal-1', 'yuniel@example.com', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "principal-1", principalType = "USER", entitled = entitled)
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
        }
    }

    private fun seedAuthorizedServiceAccount(credentialStatus: String = "ACTIVE", entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('service-principal-1', 'SERVICE_ACCOUNT', 'service-account-subject', 'https://issuer.example', 'scheduler-bot')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "service-principal-1", principalType = "SERVICE_ACCOUNT", entitled = entitled)
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
            seedServiceAccountCredential(status = credentialStatus)
        }
    }

    private fun seedServiceAccountWithoutPermission(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('service-principal-1', 'SERVICE_ACCOUNT', 'service-account-subject', 'https://issuer.example', 'scheduler-bot')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "service-principal-1", principalType = "SERVICE_ACCOUNT", entitled = entitled)
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
            seedServiceAccountCredential(status = "ACTIVE")
        }
    }

    private fun seedAuthorizedApiKeyPrincipal(credentialStatus: String = "ACTIVE", entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('api-key-principal-1', 'API_KEY', 'api-key-subject', NULL, 'integration-key')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "api-key-principal-1", principalType = "API_KEY", entitled = entitled)
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
            seedApiKeyCredential(status = credentialStatus)
        }
    }

    private fun seedApiKeyPrincipalWithoutPermission(entitled: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('api-key-principal-1', 'API_KEY', 'api-key-subject', NULL, 'integration-key')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "api-key-principal-1", principalType = "API_KEY", entitled = entitled)
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
            seedApiKeyCredential(status = "ACTIVE")
        }
    }

    private suspend fun seedWorkspaceAndRole(principalId: String, principalType: String, entitled: Boolean = false) {
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', :principalId, :principalType, 'ACTIVE')")
            .bind("principalId", principalId)
            .bind("principalType", principalType)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql("INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')").fetch().rowsUpdated().awaitSingle()
        if (entitled) {
            seedWorkspaceEntitlement()
        }
    }

    private suspend fun seedRolePermission(permissionId: String, permissionKey: String) {
        databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)")
            .bind("id", permissionId)
            .bind("permissionKey", permissionKey)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql("INSERT INTO role_permissions (role_id, permission_id) VALUES ('role-1', :permissionId)")
            .bind("permissionId", permissionId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedWorkspaceEntitlement() {
        databaseClient.sql(
            "INSERT INTO workspace_entitlements (id, workspace_id, entitlement_key, enabled) VALUES ('entitlement-1', 'workspace-1', 'workspace.access.summary', TRUE)",
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun seedTargetScope(allowedTargetIdsJson: String) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES ('permission-resource-read', 'workspace:resource:read')")
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            databaseClient.sql(
                "INSERT INTO workspace_target_scopes (id, workspace_id, principal_id, principal_type, permission_id, target_resource_type, allowed_target_ids_json) VALUES ('scope-legacy-1', 'workspace-1', 'principal-1', 'USER', 'permission-resource-read', 'RESOURCE', :allowedTargetIdsJson)",
            )
                .bind("allowedTargetIdsJson", allowedTargetIdsJson)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    private suspend fun seedServiceAccountCredential(status: String) {

        databaseClient.sql("INSERT INTO service_account_credentials (id, principal_id, provider, credential_reference, status, revoked_at) VALUES ('svc-cred-row-1', 'service-principal-1', 'https://issuer.example', 'svc-cred-1', :status, :revokedAt)")
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z")) else spec.bindNull("revokedAt", Instant::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedApiKeyCredential(status: String) {
        val verifier = org.springframework.security.crypto.bcrypt.BCrypt.hashpw("secret-value", org.springframework.security.crypto.bcrypt.BCrypt.gensalt())
        databaseClient.sql("INSERT INTO api_key_credentials (id, principal_id, lookup_key, key_prefix, secret_verifier, status, revoked_at, replaced_by_credential_id, replaced_credential_id, replaced_at) VALUES ('api-key-cred-1', 'api-key-principal-1', 'ptk_lookup', 'ptk_lookup', :verifier, :status, :revokedAt, NULL, NULL, NULL)")
            .bind("verifier", verifier)
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z")) else spec.bindNull("revokedAt", Instant::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun readApiKeyRows(): List<ApiKeyCredentialRow> =
        databaseClient.sql("SELECT id, status, replaced_by_credential_id, replaced_credential_id FROM api_key_credentials ORDER BY id")
            .map { row, _ ->
                ApiKeyCredentialRow(
                    id = requireNotNull(row.get("id", String::class.java)),
                    status = requireNotNull(row.get("status", String::class.java)),
                    replacedByCredentialId = row.get("replaced_by_credential_id", String::class.java),
                    replacedCredentialId = row.get("replaced_credential_id", String::class.java),
                )
            }
            .all()
            .collectList()
            .awaitSingle()

    private data class ApiKeyCredentialRow(
        val id: String,
        val status: String,
        val replacedByCredentialId: String?,
        val replacedCredentialId: String?,
    )

    private fun seedDirectGrant(
        effect: String,
        permissionId: String,
        permissionKey: String,
        expiresAt: String? = null,
        insertPermission: Boolean = true,
    ) {
        kotlinx.coroutines.runBlocking {
            if (insertPermission) {
                databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)")
                    .bind("id", permissionId)
                    .bind("permissionKey", permissionKey)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
            }

            databaseClient.sql("INSERT INTO workspace_direct_grants (id, workspace_id, principal_id, principal_type, permission_id, effect, expires_at, conditions_json) VALUES (:id, :workspaceId, :principalId, :principalType, :permissionId, :effect, :expiresAt, :conditionsJson)")
                .bind("id", "grant-$effect-$permissionId")
                .bind("workspaceId", "workspace-1")
                .bind("principalId", "principal-1")
                .bind("principalType", "USER")
                .bind("permissionId", permissionId)
                .bind("effect", effect)
                .let { spec ->
                    if (expiresAt == null) spec.bindNull("expiresAt", Instant::class.java) else spec.bind("expiresAt", Instant.parse(expiresAt))
                }
                .bindNull("conditionsJson", String::class.java)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    @TestConfiguration
    class TestBeans {
        @Bean
        fun reactiveJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
            when (token) {
                "valid-token" -> kotlinx.coroutines.reactor.mono {
                    Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("sub", "subject-123")
                        .claim("iss", "https://issuer.example")
                        .claim("preferred_username", "yuniel")
                        .issuedAt(Instant.parse("2026-05-15T10:15:30Z"))
                        .expiresAt(Instant.parse("2026-05-15T11:15:30Z"))
                        .build()
                }

                "service-account-token" -> kotlinx.coroutines.reactor.mono {
                    Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("sub", "service-account-subject")
                        .claim("iss", "https://issuer.example")
                        .claim("principal_type", "SERVICE_ACCOUNT")
                        .claim("credential_reference", "svc-cred-1")
                        .claim("jti", "jwt-service-1")
                        .issuedAt(Instant.parse("2026-05-15T10:15:30Z"))
                        .expiresAt(Instant.parse("2026-05-15T11:15:30Z"))
                        .build()
                }

                "ptk_lookup.secret-value" -> reactor.core.publisher.Mono.error(BadJwtException("API key is handled by API-key authentication path"))

                else -> reactor.core.publisher.Mono.error(BadJwtException("Invalid token"))
            }
        }

        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()

        @Bean
        @Primary
        fun testAuditHook(): AuditHook = CapturingAuditHook()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("proving_slice")
            .withUsername("profiletailors")
            .withPassword("profiletailors")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            if (!postgres.isRunning) {
                postgres.start()
            }

            val r2dbcUrl = "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}/${postgres.databaseName}"

            registry.add("spring.r2dbc.url") { r2dbcUrl }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }
}
