package com.profiletailors.smp.integration

import com.fasterxml.jackson.databind.ObjectMapper
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
class ResourcePreviewEndpointPostgresIntegrationTest(
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
    fun `allows resource preview when base permission exists and scope matches target on postgres`() {
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
    fun `denies resource preview when scope excludes target on postgres`() {
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
    fun `denies resource preview when base permission is missing even if scope exists on postgres`() {
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
    fun `scope resolver remains narrow without wildcard or non-workspace behavior on postgres`() {
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
            .withDatabaseName("resource_preview_slice")
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
