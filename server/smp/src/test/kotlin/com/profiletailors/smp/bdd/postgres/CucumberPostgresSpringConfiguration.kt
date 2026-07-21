package com.profiletailors.smp.bdd.postgres

import com.profiletailors.smp.bdd.glue.CommonBddTestConfiguration
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@CucumberContextConfiguration
@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "bdd.variant=postgres",
        "app.identity.registration.enabled=true",
        "management.health.defaults.enabled=false",
        "management.endpoint.health.probes.enabled=false",
        "management.endpoint.health.group.readiness.include=",
        "management.endpoint.health.group.liveness.include=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
        "publishing.linkedin.client-id=test-client-id",
        "publishing.linkedin.client-secret=test-client-secret",
        "publishing.linkedin.redirect-uri=http://localhost:9999/callback",
    ],
)
@Import(CommonBddTestConfiguration::class, PostgresBddTestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class CucumberPostgresSpringConfiguration {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("bdd_postgres_slice")
            .withUsername("profiletailors")
            .withPassword("profiletailors")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            if (!postgres.isRunning) {
                postgres.start()
            }

            val r2dbcUrl =
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(
                    PostgreSQLContainer.POSTGRESQL_PORT,
                )}/${postgres.databaseName}"

            registry.add("spring.r2dbc.url") { r2dbcUrl }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
            registry.add("bdd.liquibase.jdbc-url", postgres::getJdbcUrl)
            registry.add("bdd.liquibase.username", postgres::getUsername)
            registry.add("bdd.liquibase.password", postgres::getPassword)
        }
    }
}
