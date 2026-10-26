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
            VALUES ('d5298f21-6c5c-5672-bcac-0490740dd03a', 'USER', 'user-1', NULL, 'User One')
            """.trimIndent(),
        ).then().block()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('19ccafb8-485f-5811-9c93-1a8bfc20fbaa', 'Workspace Alpha', 'ACTIVE', NULL)
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('c809ff2a-bc89-5c49-88a3-7f7eeeecc7f7', 'Workspace Beta', 'ACTIVE', NULL)
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('708bacaa-0b89-5273-9fe9-208c9f2bbeeb', 'Archived Workspace', 'ARCHIVED', NULL)
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('1dad8f95-71c3-5d49-807b-038f52110e15', '19ccafb8-485f-5811-9c93-1a8bfc20fbaa', 'd5298f21-6c5c-5672-bcac-0490740dd03a', 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('749f2496-ece9-5128-ac90-940badc66b1b', 'c809ff2a-bc89-5c49-88a3-7f7eeeecc7f7', 'd5298f21-6c5c-5672-bcac-0490740dd03a', 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('774863db-73ac-5773-831d-0a43db3ff3b2', '708bacaa-0b89-5273-9fe9-208c9f2bbeeb', 'd5298f21-6c5c-5672-bcac-0490740dd03a', 'USER', 'ACTIVE')
            """.trimIndent(),
        )
            .then().block()
        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type)
            VALUES ('19ccafb8-485f-5811-9c93-1a8bfc20fbaa', 'd5298f21-6c5c-5672-bcac-0490740dd03a', 'USER')
            """.trimIndent(),
        )
            .then().block()
    }

    @Test
    fun `returns active workspaces for principal`() = runTest {
        val result = repository.findWorkspacesByPrincipal("d5298f21-6c5c-5672-bcac-0490740dd03a")

        assertEquals(2, result.size)
        // Ordered by name ASC: Alpha < Beta
        assertEquals("19ccafb8-485f-5811-9c93-1a8bfc20fbaa", result[0].workspaceId)
        assertEquals("Workspace Alpha", result[0].name)
        assertEquals("OWNER", result[0].role)

        assertEquals("c809ff2a-bc89-5c49-88a3-7f7eeeecc7f7", result[1].workspaceId)
        assertEquals("Workspace Beta", result[1].name)
        assertEquals("MEMBER", result[1].role)
    }

    @Test
    fun `excludes archived workspaces`() = runTest {
        val result = repository.findWorkspacesByPrincipal("d5298f21-6c5c-5672-bcac-0490740dd03a")

        assertTrue(result.none { it.workspaceId == "708bacaa-0b89-5273-9fe9-208c9f2bbeeb" })
    }

    @Test
    fun `returns empty list for unknown principal`() = runTest {
        val result = repository.findWorkspacesByPrincipal("433529d8-4a66-58d4-900e-a57a5b9cdcab")

        assertEquals(0, result.size)
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("workspace_read_repository")
    }
}
