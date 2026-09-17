package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.integration.support.IntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresIntegrationTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
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
        "platform.storage.providers.local.base-path=/tmp/smp-account-state-test-storage",
    ],
)
@Import(IntegrationTestBase.SharedTestConfiguration::class, TestStorageConfiguration::class)
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcAccountStateGatewayPostgresIntegrationTest : PostgresIntegrationTestBase() {

    override val postgresContainer: PostgreSQLContainer<*> = postgres

    private val gateway by lazy { R2dbcAccountStateGateway(databaseClient) }

    override suspend fun seedScenario() {
        seedPrincipal("user-1")
    }

    @Test
    fun `findAccountState returns ACTIVE for seeded user`() = runTest {
        assertEquals(UserAccountState.ACTIVE, gateway.findAccountState("user-1"))
    }

    @Test
    fun `findAccountState returns null for unknown principal`() = runTest {
        assertNull(gateway.findAccountState("missing"))
    }

    @Test
    fun `changeAccountState swaps state on expected match`() = runTest {
        assertTrue(gateway.changeAccountState("user-1", UserAccountState.ACTIVE, UserAccountState.DISABLED))

        assertEquals(UserAccountState.DISABLED, gateway.findAccountState("user-1"))
    }

    @Test
    fun `changeAccountState returns false on expected mismatch`() = runTest {
        assertFalse(gateway.changeAccountState("user-1", UserAccountState.DISABLED, UserAccountState.ACTIVE))

        assertEquals(UserAccountState.ACTIVE, gateway.findAccountState("user-1"))
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
