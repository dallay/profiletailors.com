package com.profiletailors.smp.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.support.CapturingAuditHook
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.reactive.server.WebTestClient
import java.sql.DriverManager
import java.time.Instant

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///proving_slice?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:proving_slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
class WorkspaceAccessSummaryEndpointIntegrationTest(
    @Autowired private val webTestClient: WebTestClient,
    @Autowired private val databaseClient: DatabaseClient,
    @Autowired private val auditHook: CapturingAuditHook,
) {
    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        auditHook.reset()
        kotlinx.coroutines.runBlocking {
            listOf(
                "DELETE FROM workspace_direct_grants",
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
    fun `returns workspace access summary for authorized member`() {
        seedAuthorizedMember()

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
    fun `rejects request when jwt is invalid`() {
        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `denies member without required permission`() {
        seedMemberWithoutPermission()

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
    fun `allows member through persisted direct allow grant`() {
        seedMemberWithoutPermission()
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
    fun `direct deny overrides role based allow on h2`() {
        seedAuthorizedMember()
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
    fun `expired direct grant is ignored on h2`() {
        seedMemberWithoutPermission()
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
    fun `returns workspace access summary for authorized service account`() {
        seedAuthorizedServiceAccount()

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
    fun `denies service account without required permission`() {
        seedServiceAccountWithoutPermission()

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
    fun `rejects revoked service-account credential before authorization executes`() {
        seedAuthorizedServiceAccount(credentialStatus = "REVOKED")

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
    fun `returns workspace access summary for authorized api key principal`() {
        seedAuthorizedApiKeyPrincipal()

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
    fun `denies api key principal without required permission`() {
        seedApiKeyPrincipalWithoutPermission()

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
    fun `rejects revoked api key credential before authorization executes`() {
        seedAuthorizedApiKeyPrincipal(credentialStatus = "REVOKED")

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
    fun `rejects inactive api key credential before authorization executes`() {
        seedAuthorizedApiKeyPrincipal(credentialStatus = "INACTIVE")

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

    private fun assertAuthorizationFacts(expected: List<AuthorizationDecisionAuditFact>) {
        assertEquals(expected, auditHook.facts)
    }

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(
            "jdbc:h2:mem:proving_slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "sa",
            "",
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

    private fun seedAuthorizedMember() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('principal-1', 'yuniel@example.com', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "principal-1", principalType = "USER")
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
        }
    }

    private fun seedMemberWithoutPermission() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('principal-1', 'yuniel@example.com', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "principal-1", principalType = "USER")
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
        }
    }

    private fun seedAuthorizedServiceAccount(credentialStatus: String = "ACTIVE") {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('service-principal-1', 'SERVICE_ACCOUNT', 'service-account-subject', 'https://issuer.example', 'scheduler-bot')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "service-principal-1", principalType = "SERVICE_ACCOUNT")
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
            seedServiceAccountCredential(status = credentialStatus)
        }
    }

    private fun seedServiceAccountWithoutPermission() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('service-principal-1', 'SERVICE_ACCOUNT', 'service-account-subject', 'https://issuer.example', 'scheduler-bot')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "service-principal-1", principalType = "SERVICE_ACCOUNT")
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
            seedServiceAccountCredential(status = "ACTIVE")
        }
    }

    private fun seedAuthorizedApiKeyPrincipal(credentialStatus: String = "ACTIVE") {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('api-key-principal-1', 'API_KEY', 'api-key-subject', NULL, 'integration-key')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "api-key-principal-1", principalType = "API_KEY")
            seedRolePermission(permissionId = "permission-1", permissionKey = "workspace:access:read")
            seedApiKeyCredential(status = credentialStatus)
        }
    }

    private fun seedApiKeyPrincipalWithoutPermission() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('api-key-principal-1', 'API_KEY', 'api-key-subject', NULL, 'integration-key')").fetch().rowsUpdated().awaitSingle()
            seedWorkspaceAndRole(principalId = "api-key-principal-1", principalType = "API_KEY")
            seedRolePermission(permissionId = "permission-2", permissionKey = "workspace:members:manage")
            seedApiKeyCredential(status = "ACTIVE")
        }
    }

    private suspend fun seedWorkspaceAndRole(principalId: String, principalType: String) {
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', :principalId, :principalType, 'ACTIVE')")
            .bind("principalId", principalId)
            .bind("principalType", principalType)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql("INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')").fetch().rowsUpdated().awaitSingle()
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
        databaseClient.sql("INSERT INTO api_key_credentials (id, principal_id, lookup_key, key_prefix, secret_verifier, status, revoked_at) VALUES ('api-key-cred-1', 'api-key-principal-1', 'ptk_lookup', 'ptk_lookup', :verifier, :status, :revokedAt)")
            .bind("verifier", verifier)
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z")) else spec.bindNull("revokedAt", Instant::class.java)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

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
    class JwtDecoderTestConfiguration {
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
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("proving_slice")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build(),
        )

        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()

        @Bean
        @Primary
        fun testAuditHook(): AuditHook = CapturingAuditHook()
    }
}
