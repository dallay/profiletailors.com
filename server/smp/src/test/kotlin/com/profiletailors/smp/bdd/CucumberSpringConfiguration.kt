package com.profiletailors.smp.bdd.fast

import com.profiletailors.smp.bdd.glue.BddTestProperties
import com.profiletailors.smp.bdd.glue.CommonBddTestConfiguration
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import io.cucumber.spring.CucumberContextConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@CucumberContextConfiguration
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "bdd.variant=fast",
        "app.identity.registration.enabled=true",
        "management.health.defaults.enabled=false",
        "management.endpoint.health.probes.enabled=false",
        "management.endpoint.health.group.readiness.include=",
        "management.endpoint.health.group.liveness.include=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "app.security.cors.allowed-origins=http://localhost",
        "spring.main.allow-bean-definition-overriding=true",
        BddTestProperties.LINKEDIN_CLIENT_ID,
        BddTestProperties.LINKEDIN_CLIENT_SECRET,
        BddTestProperties.LINKEDIN_REDIRECT_URI,
    ],
)
@Import(CommonBddTestConfiguration::class, FastBddTestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Suppress("UtilityClassWithPublicConstructor")
class CucumberSpringConfiguration {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("bdd_fast_slice")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
            registry.add("bdd.liquibase.jdbc-url", postgres::getJdbcUrl)
            registry.add("bdd.liquibase.username", postgres::getUsername)
            registry.add("bdd.liquibase.password", postgres::getPassword)
        }
    }
}

@TestConfiguration
class FastBddTestConfiguration {
    @Bean
    @Primary
    fun connectionFactory(): ConnectionFactory = PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host(CucumberSpringConfiguration.postgres.host)
            .port(
                CucumberSpringConfiguration.postgres.getMappedPort(
                    PostgreSQLContainer.POSTGRESQL_PORT,
                ),
            )
            .database(CucumberSpringConfiguration.postgres.databaseName)
            .username(CucumberSpringConfiguration.postgres.username)
            .password(CucumberSpringConfiguration.postgres.password)
            .build(),
    )
}
