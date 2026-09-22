package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyRecord
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcConfigurationIdempotencyStore
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@AutoConfigureWebTestClient
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.liquibase.enabled=true",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        "platform.storage.default=local",
        "platform.storage.providers.local.type=local",
        "platform.storage.providers.local.base-path=/tmp/smp-configuration-idempotency-store-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcConfigurationIdempotencyStorePostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    private val store by lazy { R2dbcConfigurationIdempotencyStore(databaseClient) }

    override suspend fun seedScenario() = Unit

    @Test
    fun `should return null when no idempotency record exists`() = runTest {
        assertNull(store.find(UUID.randomUUID(), "missing-key"))
    }

    @Test
    fun `should return the stored record when a claim is stored`() = runTest {
        val operatorId = UUID.randomUUID()
        val record = ConfigurationIdempotencyRecord(operatorId, "change_registration_mode", "key-1")

        assertTrue(store.claim(record))

        val found = store.find(operatorId, "key-1")
        assertEquals(record, found)
    }

    @Test
    fun `should reject a duplicate claim for the same key`() = runTest {
        val operatorId = UUID.randomUUID()
        val record = ConfigurationIdempotencyRecord(operatorId, "change_registration_mode", "key-1")

        assertTrue(store.claim(record))
        assertFalse(store.claim(record))
    }

    @Test
    fun `should propagate non-duplicate storage failures`() = runTest {
        val record = ConfigurationIdempotencyRecord(UUID.randomUUID(), "x".repeat(129), "key-1")

        assertThrows(DataAccessException::class.java) {
            kotlinx.coroutines.runBlocking { store.claim(record) }
        }
    }

    @Test
    fun `should replay the stored response after completion`() = runTest {
        val operatorId = UUID.randomUUID()
        store.claim(ConfigurationIdempotencyRecord(operatorId, "change_registration_mode", "key-1"))

        store.complete(operatorId, "key-1", """{"mode":"CLOSED"}""")

        val found = store.find(operatorId, "key-1")
        assertEquals("""{"mode":"CLOSED"}""", found?.responseJson)
    }

    @Test
    fun `should forget the record after removal`() = runTest {
        val operatorId = UUID.randomUUID()
        store.claim(ConfigurationIdempotencyRecord(operatorId, "change_registration_mode", "key-1"))

        store.remove(operatorId, "key-1")

        assertNull(store.find(operatorId, "key-1"))
    }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer("configuration_idempotency")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
