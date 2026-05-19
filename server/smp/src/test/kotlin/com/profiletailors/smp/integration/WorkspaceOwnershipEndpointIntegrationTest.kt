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
        "spring.r2dbc.url=r2dbc:h2:mem:///workspace_ownership?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:workspace_ownership;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Import(
    IntegrationTestBase.SharedTestConfiguration::class,
    WorkspaceOwnershipEndpointIntegrationTest.TestConnectionFactory::class
)
class WorkspaceOwnershipEndpointIntegrationTest : IntegrationTestBase() {

    override fun databaseName(): String = "workspace_ownership"

    override suspend fun seedScenario() {
        seedPrincipal("owner-1")
        seedPrincipal("owner-2")
        seedPrincipal("outsider-1")
        seedUserIdentity("owner-1", "owner1@example.com", "owner-one")
        seedUserIdentity("owner-2", "owner2@example.com", "owner-two")
        seedUserIdentity("outsider-1", "outsider@example.com", "outsider-one")
        seedWorkspace("workspace-1", "Profile Tailors")
        seedWorkspaceMembership("membership-1", "workspace-1", "owner-1")
        seedWorkspaceMembership("membership-2", "workspace-1", "owner-2")
        seedWorkspaceOwnership("workspace-1", "owner-1", createdBy = "owner-1")
        seedWorkspaceOwnership("workspace-1", "owner-2", createdBy = "owner-1")
    }

    @Test
    fun `adds workspace owner through protected endpoint`() {
        kotlinx.coroutines.runBlocking { auditHook.reset() }
        webTestClient.post()
            .uri("/api/tenancy/workspace-ownership/owners")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue("""{"principalId":"owner-2"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.workspaceId").isEqualTo("workspace-1")
            .jsonPath("$.ownerPrincipalIds[0]").isEqualTo("owner-1")
            .jsonPath("$.ownerPrincipalIds[1]").isEqualTo("owner-2")

        kotlin.test.assertTrue(auditHook.mutations.any { it.action == "workspace.owner.add" && it.targetId == "owner-2" })
    }

    @Test
    fun `rejects transfer when target is not active member`() {
        kotlinx.coroutines.runBlocking { auditHook.reset() }
        webTestClient.post()
            .uri("/api/tenancy/workspace-ownership/owners/transfer")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue("""{"principalId":"outsider-1"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.title").isEqualTo("Owner target must be active member")

        kotlin.test.assertTrue(auditHook.mutations.any { it.action == "workspace.owner.transfer" && it.targetId == "outsider-1" })
    }

    @Test
    fun `rejects removing last owner`() {
        kotlinx.coroutines.runBlocking {
            databaseClient.sql("DELETE FROM workspace_ownerships WHERE workspace_id = 'workspace-1' AND owner_principal_id = 'owner-2'")
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }

        webTestClient.delete()
            .uri("/api/tenancy/workspace-ownership/owners/owner-1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Workspace ownership conflict")
    }

    @TestConfiguration
    class TestConnectionFactory {
        @Bean
        @Primary
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("workspace_ownership")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build()
        )
    }
}
