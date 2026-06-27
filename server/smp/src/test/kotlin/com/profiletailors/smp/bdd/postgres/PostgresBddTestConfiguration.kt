package com.profiletailors.smp.bdd.postgres

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
@ConditionalOnProperty(name = ["bdd.variant"], havingValue = "postgres")
class PostgresBddTestConfiguration {
    @Bean
    @Primary
    fun connectionFactory(): ConnectionFactory = PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host(CucumberPostgresSpringConfiguration.postgres.host)
            .port(
                CucumberPostgresSpringConfiguration.postgres.getMappedPort(
                    org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT,
                ),
            )
            .database(CucumberPostgresSpringConfiguration.postgres.databaseName)
            .username(CucumberPostgresSpringConfiguration.postgres.username)
            .password(CucumberPostgresSpringConfiguration.postgres.password)
            .build(),
    )
}
