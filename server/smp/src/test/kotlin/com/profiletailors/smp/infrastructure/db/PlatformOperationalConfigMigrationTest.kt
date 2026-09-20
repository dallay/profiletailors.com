package com.profiletailors.smp.infrastructure.db

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformOperationalConfigMigrationTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    @Test
    fun `table exposes the key value version and timestamp columns`() = runTest {
        val columns = databaseClient.sql(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_name = 'platform_operational_config'
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get("column_name", String::class.java)) }
            .all()
            .collectList()
            .awaitSingle()
            .toSet()

        assertEquals(setOf("config_key", "config_value", "version", "updated_at"), columns)
    }

    @Test
    fun `check constraint rejects an invalid registration mode value`() = runTest {
        val error = runCatching {
            databaseClient.sql(
                """
                INSERT INTO platform_operational_config (config_key, config_value)
                VALUES ('registration.mode', 'BOGUS')
                """.trimIndent(),
            ).fetch().rowsUpdated().awaitSingle()
        }.exceptionOrNull()

        assertTrue(
            error != null,
            "Expected the CHECK constraint to reject an out-of-range registration.mode value",
        )
        val messageChain = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString("\n")
        assertTrue(
            messageChain.contains("ck_platform_operational_config_registration_mode"),
            "Expected the ck_platform_operational_config_registration_mode constraint to be reported. " +
                "Got: $messageChain",
        )
    }

    @Test
    fun `check constraint does not restrict values for unrelated configuration keys`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO platform_operational_config (config_key, config_value)
            VALUES ('some.other.key', 'ANY_VALUE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val value = databaseClient.sql(
            "SELECT config_value FROM platform_operational_config WHERE config_key = 'some.other.key'",
        )
            .map { row, _ -> requireNotNull(row.get("config_value", String::class.java)) }
            .one()
            .awaitSingle()

        assertEquals("ANY_VALUE", value)
    }

    @Test
    fun `seeds the registration mode row as CLOSED`() = runTest {
        val seeded = databaseClient.sql(
            "SELECT config_value FROM platform_operational_config WHERE config_key = 'registration.mode'",
        )
            .map { row, _ -> requireNotNull(row.get("config_value", String::class.java)) }
            .one()
            .awaitSingle()

        assertEquals("CLOSED", seeded)
    }

    @Test
    fun `master changelog includes the platform operational config changelog`() {
        val master = requireNotNull(
            javaClass.classLoader.getResourceAsStream("db/changelog/db.changelog-master.yaml"),
        ) { "Missing db/changelog/db.changelog-master.yaml" }.bufferedReader().use { it.readText() }

        assertTrue(master.contains("db/changelog/identity/010-create-platform-operational-config.yaml"))
    }

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("platform_operational_config_schema")
    }
}
