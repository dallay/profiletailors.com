package com.profiletailors.smp.integration

import com.profiletailors.smp.integration.support.WorkspaceAccessSummaryEndpointTestBase
import com.profiletailors.smp.integration.support.WorkspaceAccessSummaryEndpointTestBase.SharedTestBeans
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient

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
@Import(SharedTestBeans::class)
class WorkspaceAccessSummaryEndpointIntegrationTest : WorkspaceAccessSummaryEndpointTestBase() {

    override fun liquibaseJdbcUrl(): String =
        "jdbc:h2:mem:proving_slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"

    override fun liquibaseUsername(): String = "sa"

    override fun liquibasePassword(): String = ""

    // ── H2-exclusive test ─────────────────────────────────────────────────────

    @Test
    fun `rejects request when jwt is invalid`() {
        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ── H2-specific bean ──────────────────────────────────────────────────────

    @TestConfiguration
    class H2ConnectionFactoryConfiguration {
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
    }
}
