package com.profiletailors.smp.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
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
@Import(
    IntegrationTestBase.SharedTestConfiguration::class,
    WorkspaceMembershipEndpointIntegrationTest.TestConnectionFactory::class
)
class WorkspaceMembershipEndpointIntegrationTest : IntegrationTestBase() {

    override fun databaseName(): String = "workspace_memberships"

    override suspend fun seedScenario() {
        seedPrincipal("owner-1")
        seedPrincipal("owner-2")
        seedPrincipal("member-2")
        seedUserIdentity("owner-1", "owner1@example.com", "owner-one")
        seedUserIdentity("owner-2", "owner2@example.com", "owner-two")
        seedUserIdentity("member-2", "member2@example.com", "member-two")
        seedWorkspace("workspace-1", "Profile Tailors")
        seedWorkspaceMembership("membership-1", "workspace-1", "owner-1")
        seedWorkspaceMembership("membership-2", "workspace-1", "owner-2")
        seedWorkspaceMembership("membership-3", "workspace-1", "member-2")
        seedWorkspaceOwnership("workspace-1", "owner-1", createdBy = "owner-1")
        seedWorkspaceOwnership("workspace-1", "owner-2", createdBy = "owner-1")
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

    @TestConfiguration
    class TestConnectionFactory {
        @Bean
        @Primary
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("workspace_memberships")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build()
        )
    }
}
