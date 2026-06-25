package com.profiletailors.smp.integration.support

import org.junit.jupiter.api.Assertions.assertEquals
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

        assertEquals("r2dbc:postgresql://localhost:55432/profiletailors_test", PostgresTestContainerSupport.r2dbcUrl(container))
        assertEquals("jdbc:postgresql://localhost:55432/profiletailors_test", PostgresTestContainerSupport.jdbcUrl(container))
        assertEquals("profiletailors", PostgresTestContainerSupport.username(container))
        assertEquals("secret", PostgresTestContainerSupport.password(container))
    }

    @Test
    fun `cleanup statements delete workspace file blobs before workspaces`() {
        val statements = PostgresDatabaseCleanup.statements

        assertTrue(statements.indexOf("DELETE FROM media_assets") < statements.indexOf("DELETE FROM workspace_file_blobs"))
        assertTrue(statements.indexOf("DELETE FROM workspace_file_blobs") < statements.indexOf("DELETE FROM workspaces"))
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
