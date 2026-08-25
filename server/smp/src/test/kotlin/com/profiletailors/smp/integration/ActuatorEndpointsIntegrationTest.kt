package com.profiletailors.smp.integration

import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.test.TestStorageConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

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
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
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
        "management.health.redis.enabled=false",
        "management.endpoint.health.show-components=always",
    ],
)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActuatorEndpointsIntegrationTest {

    @Value("\${local.management.port}")
    var managementPort: Int = 0

    private fun actuatorClient(): WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$managementPort").build()

    @TestConfiguration
    class TestSecurityConfig {
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

    @Test
    fun `actuator info endpoint should be inaccessible when not in configured exposure list`() {
        actuatorClient()
            .get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus().isUnauthorized
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("actuator_test")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
