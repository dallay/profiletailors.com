package com.profiletailors.smp.bdd.fast

import com.profiletailors.smp.bdd.glue.CommonBddTestConfiguration
import io.cucumber.spring.CucumberContextConfiguration
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@CucumberContextConfiguration
@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///bdd_fast_slice?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:bdd_fast_slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "bdd.liquibase.jdbc-url=jdbc:h2:mem:bdd_fast_slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "bdd.liquibase.username=sa",
        "bdd.liquibase.password=",
        "bdd.variant=fast",
        "management.health.defaults.enabled=false",
        "management.endpoint.health.probes.enabled=false",
        "management.endpoint.health.group.readiness.include=",
        "management.endpoint.health.group.liveness.include=",
        "platform.workspace-context.header-name=X-Workspace-Id",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@Import(CommonBddTestConfiguration::class, FastBddTestConfiguration::class)
class CucumberSpringConfiguration

@TestConfiguration
class FastBddTestConfiguration {
    @Bean
    fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
        H2ConnectionConfiguration.builder()
            .inMemory("bdd_fast_slice")
            .property("MODE", "PostgreSQL")
            .property("DB_CLOSE_DELAY", "-1")
            .property("DB_CLOSE_ON_EXIT", "FALSE")
            .username("sa")
            .build(),
    )
}
