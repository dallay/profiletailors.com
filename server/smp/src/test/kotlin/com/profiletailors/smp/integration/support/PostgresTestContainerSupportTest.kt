package com.profiletailors.smp.integration.support

import io.kotest.matchers.ints.shouldBeLessThan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

class PostgresTestContainerSupportTest {

    @Test
    fun `builds R2DBC URL from shared container host port and database`() {
        val container = FakePostgresContainer(
            host = "localhost",
            mappedPort = 55432,
            databaseName = "profiletailors_test",
            username = "profiletailors",
            password = "secret",
            jdbcUrl = "jdbc:postgresql://localhost:55432/profiletailors_test",
        )

        assertEquals(
            "r2dbc:postgresql://localhost:55432/profiletailors_test",
            PostgresTestContainerSupport.r2dbcUrl(container),
        )
        assertEquals(
            "jdbc:postgresql://localhost:55432/profiletailors_test",
            PostgresTestContainerSupport.jdbcUrl(container),
        )
        assertEquals("profiletailors", PostgresTestContainerSupport.username(container))
        assertEquals("secret", PostgresTestContainerSupport.password(container))
    }

    @Test
    fun `cleanup statements delete dependent rows before parent rows`() {
        val statements = PostgresDatabaseCleanup.statements

        statements.indexOf("DELETE FROM media_assets") shouldBeLessThan
            statements.indexOf("DELETE FROM workspace_file_blobs")
        statements.indexOf("DELETE FROM workspace_file_blobs") shouldBeLessThan
            statements.indexOf("DELETE FROM workspaces")
        statements.indexOf("DELETE FROM waitlist_entries") shouldBeLessThan
            statements.indexOf("DELETE FROM waitlists WHERE id <> 'profile-tailors-launch'")
    }

    @Test
    fun `liquibase baseline tracker applies once per distinct database`() {
        val tracker = LiquibaseBaselineTracker()

        assertTrue(tracker.shouldApply("jdbc:postgresql://host:5432/db-a"))
        assertTrue(tracker.shouldApply("jdbc:postgresql://host:5432/db-b"))
        assertTrue(tracker.shouldApply("jdbc:postgresql://host:5433/db-a"))

        tracker.markApplied("jdbc:postgresql://host:5432/db-a")
        tracker.markApplied("jdbc:postgresql://host:5432/db-b")

        assertTrue(!tracker.shouldApply("jdbc:postgresql://host:5432/db-a"))
        assertTrue(!tracker.shouldApply("jdbc:postgresql://host:5432/db-b"))
        assertTrue(tracker.shouldApply("jdbc:postgresql://host:5433/db-a"))
        assertTrue(tracker.shouldApply("jdbc:postgresql://host:5432/db-c"))
    }

    @Test
    fun `password resolver returns env value when set`() {
        val resolved = PostgresTestContainerSupport.resolvePassword { "env-supplied-password" }

        assertEquals("env-supplied-password", resolved)
    }

    @Test
    fun `password resolver fails fast when env var is missing`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            PostgresTestContainerSupport.resolvePassword { null }
        }

        assertEquals(
            "${PostgresTestContainerSupport.PASSWORD_ENV} must be set to run PostgreSQL-backed tests " +
                "(e.g., export SMP_DB_TEST_PASSWORD=\$SMP_DB_PASSWORD)",
            ex.message,
        )
    }

    @Test
    fun `password resolver fails fast when env var is blank`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            PostgresTestContainerSupport.resolvePassword { "" }
        }

        assertEquals(
            "${PostgresTestContainerSupport.PASSWORD_ENV} must be set to run PostgreSQL-backed tests " +
                "(e.g., export SMP_DB_TEST_PASSWORD=\$SMP_DB_PASSWORD)",
            ex.message,
        )
    }

    private class FakePostgresContainer(
        private val host: String,
        private val mappedPort: Int,
        private val databaseName: String,
        private val username: String,
        private val password: String,
        private val jdbcUrl: String,
    ) : PostgreSQLContainer<FakePostgresContainer>("postgres:16-alpine") {
        override fun getHost(): String = host
        override fun getMappedPort(originalPort: Int): Int = mappedPort
        override fun getDatabaseName(): String = databaseName
        override fun getUsername(): String = username
        override fun getPassword(): String = password
        override fun getJdbcUrl(): String = jdbcUrl
    }
}
