package com.profiletailors.smp

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///smp_app_test?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:smp_app_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
    ]
)
class SmpApplicationTests {

	@Test
	fun contextLoads() {
	}

}
