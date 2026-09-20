package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.RegistrationModeGateway
import com.profiletailors.smp.identity.domain.RegistrationMode
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcRegistrationModeGatewayTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val gateway: RegistrationModeGateway by lazy {
        R2dbcRegistrationModeGateway(databaseClient, RegistrationConfigurationProperties())
    }

    @BeforeEach
    fun clearConfigRow() {
        runBlocking {
            databaseClient.sql("DELETE FROM platform_operational_config WHERE config_key = :key")
                .bind("key", REGISTRATION_MODE_KEY)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    @Test
    fun `falls back to the configured property mode when no row exists`() = runTest {
        val fallbackGateway = R2dbcRegistrationModeGateway(
            databaseClient,
            RegistrationConfigurationProperties(mode = RegistrationMode.INVITE_ONLY),
        )

        assertEquals(RegistrationMode.INVITE_ONLY, fallbackGateway.currentMode())
    }

    @Test
    fun `changeMode captures the previous and new value atomically in a single statement`() = runTest {
        insertConfigRow(RegistrationMode.CLOSED)

        val change = gateway.changeMode(RegistrationMode.OPEN)

        assertEquals(RegistrationMode.CLOSED, change.previousMode)
        assertEquals(RegistrationMode.OPEN, change.newMode)
        assertEquals(RegistrationMode.OPEN, gateway.currentMode())
    }

    @Test
    fun `changeMode without a persisted row fails instead of reporting a phantom transition`() = runTest {
        val outcome = runCatching { gateway.changeMode(RegistrationMode.OPEN) }

        assertTrue(outcome.isFailure)
    }

    @Test
    fun `two concurrent writers never lose an update and each reports a consistent transition`() = runTest {
        insertConfigRow(RegistrationMode.CLOSED)

        val results = coroutineScope {
            listOf(RegistrationMode.OPEN, RegistrationMode.INVITE_ONLY)
                .map { target -> async(Dispatchers.IO) { gateway.changeMode(target) } }
                .awaitAll()
        }

        val finalMode = gateway.currentMode()
        assertTrue(results.any { it.newMode == finalMode })

        val ordered = results.sortedBy { if (it.previousMode == RegistrationMode.CLOSED) 0 else 1 }
        assertEquals(RegistrationMode.CLOSED, ordered[0].previousMode)
        assertEquals(ordered[0].newMode, ordered[1].previousMode)
        assertEquals(finalMode, ordered[1].newMode)
    }

    private suspend fun insertConfigRow(mode: RegistrationMode) {
        databaseClient.sql(
            "INSERT INTO platform_operational_config (config_key, config_value) VALUES (:key, :value)",
        )
            .bind("key", REGISTRATION_MODE_KEY)
            .bind("value", mode.name)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        private const val REGISTRATION_MODE_KEY = "registration.mode"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("registration_mode_gateway")
    }
}
