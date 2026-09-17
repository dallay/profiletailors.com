package com.profiletailors.smp.platformadmin.integration

import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.platformadmin.application.UserControlIdempotencyRecord
import com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcUserControlIdempotencyStore
import com.profiletailors.smp.test.TestStorageConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
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
        "platform.storage.providers.local.base-path=/tmp/smp-idempotency-store-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcUserControlIdempotencyStorePostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    private val store by lazy { R2dbcUserControlIdempotencyStore(databaseClient) }

    override suspend fun seedScenario() = Unit

    @Test
    fun `find returns null when no record exists`() = runTest {
        assertNull(store.find(UUID.randomUUID(), "missing-key"))
    }

    @Test
    fun `claim then find returns the stored record`() = runTest {
        val operatorId = UUID.randomUUID()
        val record = UserControlIdempotencyRecord(operatorId, "disable", "user-1", "key-1")

        assertTrue(store.claim(record))

        val found = store.find(operatorId, "key-1")
        assertEquals(record, found)
    }

    @Test
    fun `claim returns false for duplicate key`() = runTest {
        val operatorId = UUID.randomUUID()
        val record = UserControlIdempotencyRecord(operatorId, "disable", "user-1", "key-1")

        assertTrue(store.claim(record))
        assertFalse(store.claim(record))
    }

    @Test
    fun `complete stores the response for replay`() = runTest {
        val operatorId = UUID.randomUUID()
        store.claim(UserControlIdempotencyRecord(operatorId, "disable", "user-1", "key-1"))

        store.complete(operatorId, "key-1", """{"principalId":"user-1"}""")

        val found = store.find(operatorId, "key-1")
        assertEquals("""{"principalId":"user-1"}""", found?.responseJson)
    }

    @Test
    fun `remove deletes the record`() = runTest {
        val operatorId = UUID.randomUUID()
        store.claim(UserControlIdempotencyRecord(operatorId, "disable", "user-1", "key-1"))

        store.remove(operatorId, "key-1")

        assertNull(store.find(operatorId, "key-1"))
    }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgresTestContainerSupport.newContainer()

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainerSupport.registerProperties(registry, postgres)
        }
    }
}
