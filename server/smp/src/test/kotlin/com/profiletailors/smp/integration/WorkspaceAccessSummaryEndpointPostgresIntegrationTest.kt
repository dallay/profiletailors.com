package com.profiletailors.smp.integration

import com.profiletailors.smp.integration.support.WorkspaceAccessSummaryEndpointTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("postgres")
@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(WorkspaceAccessSummaryEndpointTestBase.SharedTestBeans::class)
class WorkspaceAccessSummaryEndpointPostgresIntegrationTest : WorkspaceAccessSummaryEndpointTestBase() {

    override fun liquibaseJdbcUrl(): String = postgres.jdbcUrl

    override fun liquibaseUsername(): String = postgres.username

    override fun liquibasePassword(): String = postgres.password

    @org.junit.jupiter.api.Test
    fun `rejects request when jwt is invalid`() {
        webTestClient.get()
            .uri("/api/authorization/workspace-access/current")
            .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .header("X-Workspace-Id", "workspace-1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("proving_slice")
            .withUsername("profiletailors")
            .withPassword("profiletailors")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            if (!postgres.isRunning) {
                postgres.start()
            }

            val r2dbcUrl =
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}/${postgres.databaseName}"

            registry.add("spring.r2dbc.url") { r2dbcUrl }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }
}
