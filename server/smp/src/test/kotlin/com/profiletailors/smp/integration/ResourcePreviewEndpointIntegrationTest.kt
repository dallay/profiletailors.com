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
        "spring.r2dbc.url=r2dbc:h2:mem:///resource_preview_slice?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:resource_preview_slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
class ResourcePreviewEndpointIntegrationTest(
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
                "DELETE FROM user_identities",
                "DELETE FROM principals",
            ).forEach { statement ->
                databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
            }
        }
    }

    @Test
    fun `allows resource preview when base permission exists and scope matches target on h2`() {
        seedMemberWithPreviewPermission()
        seedTargetScope(allowedTargetIdsJson = "[\"resource-1\"]")

        webTestClient.get()
            .uri("/api/authorization/resources/resource-1/preview")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.resourceId").isEqualTo("resource-1")
            .jsonPath("$.principalId").isEqualTo("principal-1")
            .jsonPath("$.previewAllowed").isEqualTo(true)

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetResourcePreviewQuery",
                    requestPath = "/api/authorization/resources/resource-1/preview",
                    permission = "workspace:resource:read",
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
    fun `denies resource preview when scope excludes target on h2`() {
        seedMemberWithPreviewPermission()
        seedTargetScope(allowedTargetIdsJson = "[\"resource-2\"]")

        webTestClient.get()
            .uri("/api/authorization/resources/resource-1/preview")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Requested target resource-1 is outside the allowed scope.")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetResourcePreviewQuery",
                    requestPath = "/api/authorization/resources/resource-1/preview",
                    permission = "workspace:resource:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.SCOPE_REDUCED_TARGET,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    @Test
    fun `denies resource preview when base permission is missing even if scope exists on h2`() {
        seedMemberWithoutPreviewPermission()
        seedScopePermission()
        seedTargetScope(allowedTargetIdsJson = "[\"resource-1\"]")

        webTestClient.get()
            .uri("/api/authorization/resources/resource-1/preview")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Missing required permission workspace:resource:read.")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetResourcePreviewQuery",
                    requestPath = "/api/authorization/resources/resource-1/preview",
                    permission = "workspace:resource:read",
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
    fun `scope resolver remains narrow without wildcard or non-workspace behavior on h2`() {
        seedMemberWithPreviewPermission()
        seedTargetScope(allowedTargetIdsJson = "[\"resource-*\"]")

        webTestClient.get()
            .uri("/api/authorization/resources/resource-1/preview")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Requested target resource-1 is outside the allowed scope.")

        assertAuthorizationFacts(
            listOf(
                AuthorizationDecisionAuditFact(
                    requestName = "com.profiletailors.smp.authorization.application.GetResourcePreviewQuery",
                    requestPath = "/api/authorization/resources/resource-1/preview",
                    permission = "workspace:resource:read",
                    principalId = "principal-1",
                    workspaceId = "workspace-1",
                    decision = com.profiletailors.smp.authorization.domain.AuthorizationDecision.DENY,
                    reasonCode = AuthorizationReasonCode.SCOPE_REDUCED_TARGET,
                    roleKeys = listOf("member"),
                ),
            ),
        )
    }

    private fun assertAuthorizationFacts(expected: List<AuthorizationDecisionAuditFact>) {
        assertEquals(expected, auditHook.facts)
    }

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(
            "jdbc:h2:mem:resource_preview_slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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

    private fun seedMemberWithPreviewPermission() {
        kotlinx.coroutines.runBlocking {
            seedPrincipalAndMembership()
            seedRolePermission(permissionId = "permission-resource-read", permissionKey = "workspace:resource:read")
        }
    }

    private fun seedMemberWithoutPreviewPermission() {
        kotlinx.coroutines.runBlocking {
            seedPrincipalAndMembership()
            seedRolePermission(permissionId = "permission-members-manage", permissionKey = "workspace:members:manage")
        }
    }

    private suspend fun seedPrincipalAndMembership() {
        databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('principal-1', 'yuniel@example.com', 'yuniel')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', 'principal-1', 'USER', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
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

    private fun seedTargetScope(allowedTargetIdsJson: String) {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql(
                "INSERT INTO workspace_target_scopes (id, workspace_id, principal_id, principal_type, permission_id, target_resource_type, allowed_target_ids_json) VALUES ('scope-1', 'workspace-1', 'principal-1', 'USER', 'permission-resource-read', 'RESOURCE', :allowedTargetIdsJson)",
            )
                .bind("allowedTargetIdsJson", allowedTargetIdsJson)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    private fun seedScopePermission() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES ('permission-resource-read', 'workspace:resource:read')")
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
                else -> reactor.core.publisher.Mono.error(BadJwtException("Invalid token"))
            }
        }

        @Bean
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("resource_preview_slice")
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
