package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
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
class R2dbcWorkspaceReadRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer
    private lateinit var repository: R2dbcWorkspaceReadRepository

    @BeforeEach
    fun setUp() {
        repository = R2dbcWorkspaceReadRepository(databaseClient)

        // Seed data
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('user-1', 'USER', 'user-1', NULL, 'User One')
            """.trimIndent(),
        ).then().block()
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-a', 'Workspace Alpha', 'ACTIVE', NULL)",
        )
            .then().block()
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-b', 'Workspace Beta', 'ACTIVE', NULL)",
        )
            .then().block()
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-c', 'Archived Workspace', 'ARCHIVED', NULL)",
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('m1', 'ws-a', 'user-1', 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('m2', 'ws-b', 'user-1', 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('m3', 'ws-c', 'user-1', 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type)
            VALUES ('ws-a', 'user-1', 'USER')
            """.trimIndent(),
        )
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

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("workspace_read_repository")
    }
}
