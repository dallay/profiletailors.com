package com.profiletailors.smp.integration

import com.profiletailors.smp.integration.WorkspaceMembershipEndpointIntegrationTest.TestJwtConfiguration
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
        "spring.r2dbc.url=r2dbc:h2:mem:///workspace_memberships?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:workspace_memberships;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Import(TestJwtConfiguration::class)
class WorkspaceMembershipEndpointIntegrationTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var databaseClient: DatabaseClient

    @Autowired
    lateinit var auditHook: com.profiletailors.smp.integration.support.CapturingAuditHook

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        kotlinx.coroutines.runBlocking {
            cleanupStatements().forEach { statement ->
                databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
            }
            seedMembershipScenario()
        }
    }

    @Test
    fun `rejects suspending last owner membership`() {
        kotlinx.coroutines.runBlocking {
            auditHook.reset()
            databaseClient.sql("DELETE FROM workspace_ownerships WHERE workspace_id = 'workspace-1' AND owner_principal_id = 'owner-2'")
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }

        webTestClient.patch()
            .uri("/api/tenancy/workspace-memberships/owner-1/status")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue("""{"status":"SUSPENDED"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Workspace ownership conflict")

        kotlin.test.assertTrue(auditHook.mutations.any { it.action == "workspace.membership.status.update" && it.targetId == "owner-1" })
    }

    @Test
    fun `allows removing non owner membership`() {
        kotlinx.coroutines.runBlocking { auditHook.reset() }
        webTestClient.patch()
            .uri("/api/tenancy/workspace-memberships/member-2/status")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue("""{"status":"REMOVED"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.principalId").isEqualTo("member-2")
            .jsonPath("$.status").isEqualTo("REMOVED")

        kotlin.test.assertTrue(auditHook.mutations.any { it.action == "workspace.membership.status.update" && it.targetId == "member-2" })
    }

    private suspend fun seedMembershipScenario() {
        seedPrincipal("owner-1")
        seedPrincipal("owner-2")
        seedPrincipal("member-2")
        databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('owner-1', 'owner1@example.com', 'owner-one')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('owner-2', 'owner2@example.com', 'owner-two')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO user_identities (principal_id, email, username) VALUES ('member-2', 'member2@example.com', 'member-two')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', 'owner-1', 'USER', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-2', 'workspace-1', 'owner-2', 'USER', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-3', 'workspace-1', 'member-2', 'USER', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type, created_by) VALUES ('workspace-1', 'owner-1', 'USER', 'owner-1')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type, created_by) VALUES ('workspace-1', 'owner-2', 'USER', 'owner-1')").fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedPrincipal(principalId: String) {
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES (:id, 'USER', :subject, 'https://issuer.example', :displayIdentity)",
        )
            .bind("id", principalId)
            .bind("subject", "subject-$principalId")
            .bind("displayIdentity", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun cleanupStatements(): List<String> = listOf(
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
            "jdbc:h2:mem:workspace_memberships;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
        @Primary
        fun testAuditHook(): com.profiletailors.smp.integration.support.CapturingAuditHook =
            com.profiletailors.smp.integration.support.CapturingAuditHook()

        @Bean
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("workspace_memberships")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build(),
        )

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
