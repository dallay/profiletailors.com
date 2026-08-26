package com.profiletailors.smp.leadcapture.integration

import com.profiletailors.ratelimit.infrastructure.gateway.Bucket4jRateLimiter
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
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

    @Autowired
    private lateinit var bucket4jRateLimiter: Bucket4jRateLimiter

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

    @BeforeEach
    fun resetRateLimiterBuckets() {
        // Each test method must start with a fresh WAITLIST bucket cache; otherwise the in-process
        // bucket from a prior test method (which all share the same loopback remoteAddress under
        // bindToServer/RANDOM_PORT) would already be exhausted and short-circuit later tests.
        bucket4jRateLimiter.clearCache()
    }

    @Test
    fun `11th request from the same IP within a minute returns 429 rate_limited`() {
        val capacity = 10

        // The 11 calls intentionally rotate `X-Forwarded-For` per request. After the P2 Codex
        // remediation, the shared `RateLimitingFilter` no longer trusts client-supplied forwarded
        // headers and keys buckets on `remoteAddress` only. Every WebTestClient call below comes
        // from the same loopback socket, so all 11 calls share the same WAITLIST bucket and the
        // 11th request still receives 429 — proving the limit cannot be bypassed by rotating the
        // header.
        for (i in 1..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", "198.51.100.$i")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("user-$i@example.com"))
                .exchange()
                .expectStatus().isAccepted
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .header("X-Forwarded-For", "198.51.100.250")
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
        // `X-Forwarded-For` is intentionally ignored by the filter (P2 remediation), so every
        // WebTestClient call lands on the same loopback remoteAddress and the bucket is keyed by
        // path. Different waitlists therefore remain independent.
        val spoofedClient = "10.0.0.4"

        for (i in 1..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", spoofedClient)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("launch-user-$i@example.com"))
                .exchange()
                .expectStatus().isAccepted
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-beta/entries")
            .header("X-Forwarded-For", spoofedClient)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("beta-user@example.com"))
            .exchange()
            .expectStatus().isAccepted
    }

    @Test
    fun `rate limit applies even on duplicate joins and validation errors`() {
        val capacity = 10
        val spoofedClient = "10.0.0.2"

        for (i in 1..5) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", spoofedClient)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("user-rl@example.com"))
                .exchange()
                .expectStatus().isAccepted
        }

        for (i in 6..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", spoofedClient)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJoinRequest())
                .exchange()
                .expectStatus().isBadRequest
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .header("X-Forwarded-For", spoofedClient)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("user-overflow-2@example.com"))
            .exchange()
            .expectStatus().isEqualTo(429)
    }

    @Test
    fun `rate limited response includes Retry-After header`() {
        val capacity = 10
        val spoofedClient = "10.0.0.3"

        for (i in 1..capacity) {
            webTestClient.post()
                .uri("/api/waitlists/profile-tailors-launch/entries")
                .header("X-Forwarded-For", spoofedClient)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJoinRequest("header-user-$i@example.com"))
                .exchange()
        }

        webTestClient.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .header("X-Forwarded-For", spoofedClient)
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
