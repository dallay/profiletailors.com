package com.profiletailors.smp.integration

import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.test.TestStorageConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@ContextConfiguration(
    classes = [TestStorageConfiguration::class],
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
        "management.health.redis.enabled=false",
        "platform.storage.default=attachments",
        "platform.storage.providers.attachments.type=local",
        "platform.storage.providers.attachments.base-path=./tmp/actuator-prometheus-test-storage",
        "media.storage.bucket=attachments",
    ],
)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActuatorPrometheusSecurityIntegrationTest {

    @Value("\${local.management.port}")
    var managementPort: Int = 0

    private fun actuatorClient(): WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$managementPort").build()

    @Test
    fun `should reject anonymous prometheus scrape with 401 through the production chain`() {
        actuatorClient()
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isUnauthorized
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("actuator_prometheus_slice")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
