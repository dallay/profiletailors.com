package com.profiletailors.smp.leadcapture.integration

import com.profiletailors.smp.SmpApplication
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WaitlistDistributedRateLimitE2ETest {

    private lateinit var replicaA: ConfigurableApplicationContext
    private lateinit var replicaB: ConfigurableApplicationContext
    private lateinit var clientA: WebTestClient
    private lateinit var clientB: WebTestClient

    @BeforeAll
    fun setUp() {
        replicaA = startReplica("replica-a", liquibaseEnabled = true)
        replicaB = startReplica("replica-b", liquibaseEnabled = false)

        clientA = webClientFor(replicaA)
        clientB = webClientFor(replicaB)

        seedWaitlist(replicaA)
    }

    @AfterAll
    fun tearDown() {
        if (this::replicaA.isInitialized) {
            replicaA.close()
        }
        if (this::replicaB.isInitialized) {
            replicaB.close()
        }
    }

    @Test
    fun `distributed waitlist bucket is shared across two SMP replicas`() {
        clientA.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("shared-a-1@example.com"))
            .exchange()
            .expectStatus().isAccepted

        clientB.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("shared-b-2@example.com"))
            .exchange()
            .expectStatus().isAccepted

        clientA.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("shared-a-3@example.com"))
            .exchange()
            .expectStatus().isAccepted

        // Capacity is 3 requests/minute; the fourth combined request across replicas must be denied.
        clientB.post()
            .uri("/api/waitlists/profile-tailors-launch/entries")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validJoinRequest("shared-b-4@example.com"))
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("RATE_LIMIT_EXCEEDED")
    }

    private fun startReplica(replicaName: String, liquibaseEnabled: Boolean): ConfigurableApplicationContext {
        val postgresPort = postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
        val r2dbcUrl = "r2dbc:postgresql://${postgres.host}:$postgresPort/${postgres.databaseName}"
        val jdbcUrl = "jdbc:postgresql://${postgres.host}:$postgresPort/${postgres.databaseName}"
        val redisUri = "redis://${redis.host}:${redis.firstMappedPort}"

        return SpringApplicationBuilder(SmpApplication::class.java)
            .properties(
                mapOf(
                    "server.port" to "0",
                    "spring.main.allow-bean-definition-overriding" to "true",
                    "spring.r2dbc.url" to r2dbcUrl,
                    "spring.r2dbc.username" to postgres.username,
                    "spring.r2dbc.password" to postgres.password,
                    "spring.liquibase.enabled" to liquibaseEnabled.toString(),
                    "spring.liquibase.url" to jdbcUrl,
                    "spring.liquibase.user" to postgres.username,
                    "spring.liquibase.password" to postgres.password,
                    "application.rate-limit.enabled" to "true",
                    "application.rate-limit.auth.enabled" to "false",
                    "application.rate-limit.business.enabled" to "false",
                    "application.rate-limit.resume.enabled" to "false",
                    "application.rate-limit.waitlist.enabled" to "true",
                    "application.rate-limit.waitlist.endpoints" to "/api/waitlists",
                    "application.rate-limit.waitlist.limit.name" to "waitlist-e2e",
                    "application.rate-limit.waitlist.limit.capacity" to "3",
                    "application.rate-limit.waitlist.limit.refill-tokens" to "3",
                    "application.rate-limit.waitlist.limit.refill-duration" to "PT1M",
                    "application.rate-limit.store.distributed-enabled" to "true",
                    "application.rate-limit.store.type" to "REDIS",
                    "application.rate-limit.store.redis.uri" to redisUri,
                    "application.rate-limit.store.redis.key-prefix" to "smp:e2e:ratelimit:",
                    "platform.storage.default" to "local",
                    "platform.storage.providers.local.type" to "local",
                    "platform.storage.providers.local.base-path" to "/tmp/smp-rate-limit-e2e-storage",
                    "smp.replica.name" to replicaName,
                ),
            )
            .run()
    }

    private fun webClientFor(context: ConfigurableApplicationContext): WebTestClient {
        val port = context.environment.getProperty("local.server.port")
            ?: error("local.server.port not set — server may not have started")

        return WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }

    private fun seedWaitlist(context: ConfigurableApplicationContext) {
        val databaseClient = context.getBean(DatabaseClient::class.java)
        databaseClient.sql(
            """
            INSERT INTO waitlists (id, key, name, context, status)
            VALUES ('profile-tailors-launch', 'profile-tailors-launch', 'Profile Tailors Launch', 'profile-tailors', 'ACTIVE')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).fetch().rowsUpdated().block()
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
            "version": "2026-07-31"
          },
          "metadata": { "utm_source": "newsletter" }
        }
        """.trimIndent()

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("waitlist_distributed_e2e")

        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
    }
}
