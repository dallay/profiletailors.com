package com.profiletailors.smp.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.WorkspaceAuditEventsEndpointIntegrationTest.TestJwtConfiguration
import com.profiletailors.smp.integration.support.CapturingAuditHook
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.sql.DriverManager

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///workspace_audit_events?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:workspace_audit_events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "platform.hooks.audit.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Import(TestJwtConfiguration::class)
class WorkspaceAuditEventsEndpointIntegrationTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var databaseClient: DatabaseClient

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        kotlinx.coroutines.runBlocking {
            cleanupStatements().forEach { statement ->
                databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
            }
            seedAuditEvents()
            seedAuthorizationData()
        }
    }

    @Test
    fun `lists workspace audit events with filters and cursor pagination for authorized reader`() {
        val firstPageBody = webTestClient.get()
            .uri("/api/governance/audit-events?targetType=WORKSPACE_OWNER&action=workspace.owner.add&eventType=MUTATION&actorPrincipalId=owner-1&createdAfter=2026-05-20T11:59:00Z&createdBefore=2026-05-20T12:03:00Z&limit=1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].id").isEqualTo("audit-2")
            .jsonPath("$.page.cursor").doesNotExist()
            .jsonPath("$.page.limit").isEqualTo(1)
            .jsonPath("$.page.returned").isEqualTo(1)
            .jsonPath("$.page.hasMore").isEqualTo(true)
            .returnResult()
            .responseBody

        val firstPageJson = ObjectMapper().readTree(String(requireNotNull(firstPageBody)))
        val nextCursor = firstPageJson.path("page").path("nextCursor").asText()
        assertTrue(nextCursor.isNotBlank())

        webTestClient.get()
            .uri("/api/governance/audit-events?targetType=WORKSPACE_OWNER&action=workspace.owner.add&eventType=MUTATION&actorPrincipalId=owner-1&createdAfter=2026-05-20T11:59:00Z&createdBefore=2026-05-20T12:03:00Z&cursor=$nextCursor&limit=1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].id").isEqualTo("audit-1")
            .jsonPath("$.page.cursor").isEqualTo(nextCursor)
            .jsonPath("$.page.limit").isEqualTo(1)
            .jsonPath("$.page.returned").isEqualTo(1)
            .jsonPath("$.page.hasMore").isEqualTo(false)
            .jsonPath("$.page.nextCursor").doesNotExist()
    }

    @Test
    fun `denies workspace audit events when principal lacks audit read permission`() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("DELETE FROM role_permissions WHERE role_id = 'role-audit-reader'")
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }

        webTestClient.get()
            .uri("/api/governance/audit-events")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.title").isEqualTo("Authorization denied")
    }

    @Test
    fun `returns bad request when cursor is invalid`() {
        webTestClient.get()
            .uri("/api/governance/audit-events?cursor=%%%&limit=1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.title").isEqualTo("Invalid audit cursor")
    }

    private suspend fun seedAuthorizationData() {
        databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('owner-1', 'USER', 'subject-owner-1', 'https://issuer.example', 'owner-1')")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('owner-1', 'owner1@example.com', 'owner-one')")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', 'owner-1', 'USER', 'ACTIVE')")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO roles (id, role_key, category) VALUES ('role-audit-reader', 'audit-reader', 'WORKSPACE')")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-audit-reader')")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES ('permission-audit-read', 'workspace:audit:read')")
            .fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO role_permissions (role_id, permission_id) VALUES ('role-audit-reader', 'permission-audit-read')")
            .fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedAuditEvents() {
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id, target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                'audit-1', 'MUTATION', 'workspace.owner.add', 'owner-1', 'workspace-1', 'WORKSPACE_OWNER', 'owner-2', 'SUCCESS', '[]', '{"ownerPrincipalId":"owner-2"}', TIMESTAMP WITH TIME ZONE '2026-05-20T12:01:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id, target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                'audit-2', 'MUTATION', 'workspace.owner.add', 'owner-1', 'workspace-1', 'WORKSPACE_OWNER', 'owner-3', 'SUCCESS', '[]', '{"ownerPrincipalId":"owner-3"}', TIMESTAMP WITH TIME ZONE '2026-05-20T12:02:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id, target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                'audit-3', 'MUTATION', 'workspace.membership.status.update', 'owner-1', 'workspace-1', 'WORKSPACE_MEMBERSHIP', 'member-2', 'SUCCESS', '[]', '{"targetStatus":"REMOVED"}', TIMESTAMP WITH TIME ZONE '2026-05-20T12:03:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM audit_events",
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
    )

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(
            "jdbc:h2:mem:workspace_audit_events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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

    @TestConfiguration
    class TestJwtConfiguration {
        @Bean
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("workspace_audit_events")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build(),
        )

        @Bean
        @Primary
        fun testAuditHook(): CapturingAuditHook = CapturingAuditHook()

        @Bean
        fun reactiveJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
            when (token) {
                "owner-token" -> reactor.core.publisher.Mono.just(
                    Jwt.withTokenValue(token)
                        .subject("subject-owner-1")
                        .header("alg", "none")
                        .claim("sub", "subject-owner-1")
                        .claim("iss", "https://issuer.example")
                        .claim("principal_id", "owner-1")
                        .claim("principal_type", "USER")
                        .issuedAt(java.time.Instant.parse("2026-05-20T10:15:30Z"))
                        .expiresAt(java.time.Instant.parse("2026-05-20T11:15:30Z"))
                        .build(),
                )
                else -> reactor.core.publisher.Mono.error(BadJwtException("Invalid token"))
            }
        }
    }
}
