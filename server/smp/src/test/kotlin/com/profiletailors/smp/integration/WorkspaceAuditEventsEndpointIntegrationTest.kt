package com.profiletailors.smp.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.support.IntegrationTestBase
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders

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
@Import(
    IntegrationTestBase.SharedTestConfiguration::class,
    WorkspaceAuditEventsEndpointIntegrationTest.TestConnectionFactory::class
)
class WorkspaceAuditEventsEndpointIntegrationTest : IntegrationTestBase() {

    override fun databaseName(): String = "workspace_audit_events"

    override suspend fun seedScenario() {
        seedAuthorizationData()
        seedAuditEvents()
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
        seedPrincipal("owner-1")
        seedUserIdentity("owner-1", "owner1@example.com", "owner-one")
        seedWorkspace("workspace-1", "Profile Tailors")
        seedWorkspaceMembership("membership-1", "workspace-1", "owner-1")
        
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

    @TestConfiguration
    class TestConnectionFactory {
        @Bean
        @Primary
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("workspace_audit_events")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build()
        )
    }
}
