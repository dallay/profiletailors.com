package com.profiletailors.smp.integration

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
    fun `rejects request when workspace header is missing`() {
        seedAuthorizedMember()

        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `rejects request when jwt is missing`() {
        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized
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

    private fun assertAuthorizationFacts(expected: List<AuthorizationDecisionAuditFact>) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, auditHook.facts)
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
            databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', 'principal-1', 'USER', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES ('permission-1', 'workspace:access:read')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO role_permissions (role_id, permission_id) VALUES ('role-1', 'permission-1')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')").fetch().rowsUpdated().awaitSingle()
        }
    }

    private fun seedMemberWithoutPermission() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('principal-1', 'yuniel@example.com', 'yuniel')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', 'principal-1', 'USER', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES ('permission-2', 'workspace:members:manage')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO role_permissions (role_id, permission_id) VALUES ('role-1', 'permission-2')").fetch().rowsUpdated().awaitSingle()
            databaseClient.sql("INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')").fetch().rowsUpdated().awaitSingle()
        }
    }

    @TestConfiguration
    class JwtDecoderTestConfiguration {
        @Bean
        fun reactiveJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
            if (token != "valid-token") {
                return@ReactiveJwtDecoder reactor.core.publisher.Mono.error(BadJwtException("Invalid token"))
            }

            kotlinx.coroutines.reactor.mono {
                Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("sub", "subject-123")
                    .claim("iss", "https://issuer.example")
                    .claim("preferred_username", "yuniel")
                    .issuedAt(Instant.parse("2026-05-15T10:15:30Z"))
                    .expiresAt(Instant.parse("2026-05-15T11:15:30Z"))
                    .build()
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
        @Primary
        fun testAuditHook(): AuditHook = CapturingAuditHook()
    }
}
