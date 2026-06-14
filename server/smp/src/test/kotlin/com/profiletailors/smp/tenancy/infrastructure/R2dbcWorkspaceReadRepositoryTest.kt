package com.profiletailors.smp.tenancy.infrastructure

import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.util.UUID

class R2dbcWorkspaceReadRepositoryTest {

    private lateinit var databaseClient: DatabaseClient
    private lateinit var repository: R2dbcWorkspaceReadRepository

    @BeforeEach
    fun setUp() {
        val uid = UUID.randomUUID().toString().substring(0, 8)
        val r2dbcUrl = "r2dbc:h2:mem:///wsread_$uid?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"

        val connectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .url(r2dbcUrl)
                .username("sa")
                .password("")
                .build(),
        )

        databaseClient = DatabaseClient.create(connectionFactory)
        repository = R2dbcWorkspaceReadRepository(databaseClient)

        // Create schema and seed data via R2DBC (block() since @BeforeEach is not suspend)
        databaseClient.sql(
            """CREATE TABLE workspaces (
                id VARCHAR(36) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                icon VARCHAR(64) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""",
        ).then().block()

        databaseClient.sql(
            """CREATE TABLE workspace_memberships (
                id VARCHAR(36) PRIMARY KEY,
                workspace_id VARCHAR(36) NOT NULL,
                principal_id VARCHAR(255) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                icon VARCHAR(64) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""",
        ).then().block()

        databaseClient.sql(
            """CREATE TABLE workspace_ownerships (
                workspace_id VARCHAR(36) NOT NULL,
                owner_principal_id VARCHAR(255) NOT NULL,
                owner_principal_type VARCHAR(50) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (workspace_id, owner_principal_id)
            )""",
        ).then().block()

        // Seed data
        databaseClient.sql("INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-a', 'Workspace Alpha', 'ACTIVE')")
            .then().block()
        databaseClient.sql("INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-b', 'Workspace Beta', 'ACTIVE')")
            .then().block()
        databaseClient.sql("INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-c', 'Archived Workspace', 'ARCHIVED')")
            .then().block()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, status) VALUES ('m1', 'ws-a', 'user-1', 'ACTIVE')")
            .then().block()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, status) VALUES ('m2', 'ws-b', 'user-1', 'ACTIVE')")
            .then().block()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, status) VALUES ('m3', 'ws-c', 'user-1', 'ACTIVE')")
            .then().block()
        databaseClient.sql("INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type) VALUES ('ws-a', 'user-1', 'USER')")
            .then().block()
    }

    @Test
    fun `returns active workspaces for principal`() = runTest {
        val result = repository.findWorkspacesByPrincipal("user-1")

        assertEquals(2, result.size)
        // Ordered by name ASC: Alpha < Beta
        assertEquals("ws-a", result[0].workspaceId)
        assertEquals("Workspace Alpha", result[0].name)
        assertEquals("OWNER", result[0].role)

        assertEquals("ws-b", result[1].workspaceId)
        assertEquals("Workspace Beta", result[1].name)
        assertEquals("MEMBER", result[1].role)
    }

    @Test
    fun `excludes archived workspaces`() = runTest {
        val result = repository.findWorkspacesByPrincipal("user-1")

        assertTrue(result.none { it.workspaceId == "ws-c" })
    }

    @Test
    fun `returns empty list for unknown principal`() = runTest {
        val result = repository.findWorkspacesByPrincipal("unknown-user")

        assertEquals(0, result.size)
    }
}
