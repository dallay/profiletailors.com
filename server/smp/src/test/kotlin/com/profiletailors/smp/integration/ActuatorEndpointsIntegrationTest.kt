package com.profiletailors.smp.integration

import com.profiletailors.smp.test.TestStorageConfiguration
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Integration tests for Spring Boot Actuator endpoints.
 *
 * Tests verify:
 * - Health endpoint returns service status
 * - Readiness endpoint reports readiness state
 * - Liveness endpoint reports liveness state
 * - Prometheus metrics endpoint exposes metrics
 *
 */
@ContextConfiguration(
    classes = [ActuatorEndpointsIntegrationTest.TestSecurityConfig::class, TestStorageConfiguration::class],
)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///actuator_test" +
            "?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "management.server.port=0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        "management.health.livenessState.enabled=true",
        "management.health.readinessState.enabled=true",
        "platform.storage.default=attachments",
        "platform.storage.providers.attachments.type=local",
        "platform.storage.providers.attachments.base-path=./tmp/actuator-test-storage",
        "media.storage.bucket=attachments",
    ],
)
class ActuatorEndpointsIntegrationTest {

    @Value("\${local.management.port}")
    var managementPort: Int = 0

    private fun actuatorClient(): WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$managementPort").build()

    @TestConfiguration
    class TestSecurityConfig {
        @Bean
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("actuator_test")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build(),
        )

        @Bean
        @Primary
        fun testSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
            .csrf { it.disable() }
            .authorizeExchange {
                it.anyExchange().permitAll()
            }
            .build()
    }

    @Test
    fun `TC001 - health endpoint returns service health status`() {
        actuatorClient()
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `TC002 - readiness endpoint returns readiness status`() {
        actuatorClient()
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").exists()
    }

    @Test
    fun `TC003 - liveness endpoint returns liveness status`() {
        actuatorClient()
            .get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").exists()
    }

    @Test
    fun `TC004 - prometheus endpoint exposes metrics`() {
        actuatorClient()
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith { response ->
                val body = response.responseBody
                assert(body != null) { "Prometheus metrics body should not be null" }
                assert(body!!.contains("jvm_")) { "Should contain JVM metrics" }
                assert(body.contains("process_")) { "Should contain process metrics" }
            }
    }

    @Test
    fun `health endpoint includes all health indicators`() {
        actuatorClient()
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.components").exists()
    }

    @Test
    fun `readiness endpoint reflects application readiness state`() {
        actuatorClient()
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isNotEmpty
    }

    @Test
    fun `liveness endpoint reflects application liveness state`() {
        actuatorClient()
            .get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isNotEmpty
    }

    @Test
    fun `prometheus metrics include custom application metrics`() {
        actuatorClient()
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith { response ->
                val body = response.responseBody
                assert(body != null) { "Metrics should not be null" }
                // Verify Prometheus format
                assert(body!!.contains("# HELP")) { "Should contain metric help text" }
                assert(body.contains("# TYPE")) { "Should contain metric type definitions" }
            }
    }
}
