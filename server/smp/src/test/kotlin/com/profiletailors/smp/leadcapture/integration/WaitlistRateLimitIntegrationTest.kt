package com.profiletailors.smp.leadcapture.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        "application.rate-limit.enabled=true",
        "application.rate-limit.waitlist.enabled=true",
        "application.rate-limit.waitlist.endpoints=/api/waitlists",
        "application.rate-limit.waitlist.limit.name=waitlist-test",
        "application.rate-limit.waitlist.limit.capacity=10",
        "application.rate-limit.waitlist.limit.refill-tokens=10",
        "application.rate-limit.waitlist.limit.refill-duration=1m",
        "platform.storage.default=local",
        "platform.storage.providers.local.type=local",
        "platform.storage.providers.local.base-path=/tmp/smp-rate-limit-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WaitlistRateLimitIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    override suspend fun seedScenario() {
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status)
            VALUES ('profile-tailors-beta', 'profile-tailors-beta', 'Profile Tailors Beta', 'profile-tailors', 'ACTIVE')
            """.trimIndent(),
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM waitlist_entries",
    ) + super.cleanupStatements()

    @Test
    fun `11th request from the same IP within a minute returns 429 rate_limited`() {
        val capacity = 10
        val ip = "10.0.0.1"

        for (i in 1..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("user-$i@example.com"))
                .exchange()
                .expectStatus().isAccepted
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .header("X-Forwarded-For", ip)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("user-overflow@example.com"))
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("RATE_LIMIT_EXCEEDED")
            .jsonPath("$.error.message").isEqualTo("Too many attempts. Please try again later.")
    }

    @Test
    fun `same IP can join a different waitlist after exhausting the first waitlist quota`() {
        val capacity = 10
        val ip = "10.0.0.4"

        for (i in 1..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("launch-user-$i@example.com"))
                .exchange()
                .expectStatus().isAccepted
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-beta/entries")
            .header("X-Forwarded-For", ip)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("beta-user@example.com"))
            .exchange()
            .expectStatus().isAccepted
    }

    @Test
    fun `rate limit applies even on duplicate joins and validation errors`() {
        val capacity = 10
        val ip = "10.0.0.2"

        for (i in 1..5) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("user-rl@example.com"))
                .exchange()
                .expectStatus().isAccepted
        }

        for (i in 6..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJoinRequest())
                .exchange()
                .expectStatus().isBadRequest
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .header("X-Forwarded-For", ip)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("user-overflow-2@example.com"))
            .exchange()
            .expectStatus().isEqualTo(429)
    }

    @Test
    fun `rate limited response includes Retry-After header`() {
        val capacity = 10
        val ip = "10.0.0.3"

        for (i in 1..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("header-user-$i@example.com"))
                .exchange()
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .header("X-Forwarded-For", ip)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("header-overflow@example.com"))
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().exists("Retry-After")
            .expectHeader().exists("X-RateLimit-Limit")
    }

    private fun validJoinRequest(email: String): String =
        """
        {
          "email": "$email",
          "source": "marketing-site",
          "formId": "waitlist-hero",
          "locale": "en",
          "consent": {
            "earlyAccess": true,
            "marketing": false,
            "version": "2026-07-17"
          },
          "metadata": { "utm_source": "newsletter" }
        }
        """.trimIndent()

    private fun invalidJoinRequest(): String =
        """
        {
          "email": "",
          "source": "marketing-site",
          "consent": {
            "earlyAccess": true,
            "version": "2026-07-17"
          }
        }
        """.trimIndent()

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgresTestContainerSupport.newContainer("waitlist_ratelimit_test")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
