package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcWorkspaceOwnershipRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer
    private lateinit var repository: R2dbcWorkspaceOwnershipRepository

    @BeforeEach
    fun setUp() {
        repository = R2dbcWorkspaceOwnershipRepository(databaseClient)

        // Seed some basic data
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('owner-1', 'USER', 'owner-1', NULL, 'Owner One')
            """.trimIndent(),
        ).then().block()

        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('owner-2', 'USER', 'owner-2', NULL, 'Owner Two')
            """.trimIndent(),
        ).then().block()

        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-1', 'Workspace Alpha', 'ACTIVE', NULL)",
        ).then().block()
    }

    @Test
    fun `add, exists, and findByWorkspaceId works correctly`() = runTest {
        val ownership = WorkspaceOwnership(
            workspaceId = "ws-1",
            ownerPrincipalId = "owner-1",
            ownerPrincipalType = PrincipalType.USER,
            createdBy = "owner-1",
            createdAt = Instant.now(),
        )

        repository.add(ownership)

        assertTrue(repository.exists("ws-1", "owner-1"))
        assertFalse(repository.exists("ws-1", "owner-2"))

        val owners = repository.findByWorkspaceId("ws-1")
        assertEquals(1, owners.size)
        val firstOwner = owners.first()
        assertEquals("ws-1", firstOwner.workspaceId)
        assertEquals("owner-1", firstOwner.ownerPrincipalId)
        assertEquals(PrincipalType.USER, firstOwner.ownerPrincipalType)
    }

    @Test
    fun `removeIfReplacementExists works correctly`() = runTest {
        val o1 = WorkspaceOwnership(
            workspaceId = "ws-1",
            ownerPrincipalId = "owner-1",
            ownerPrincipalType = PrincipalType.USER,
        )
        val o2 = WorkspaceOwnership(
            workspaceId = "ws-1",
            ownerPrincipalId = "owner-2",
            ownerPrincipalType = PrincipalType.USER,
        )

        repository.add(o1)

        // Attempting to remove the only owner should fail (return false)
        val removedSingle = repository.removeIfReplacementExists("ws-1", "owner-1")
        assertFalse(removedSingle)
        assertTrue(repository.exists("ws-1", "owner-1"))

        // Add a second owner
        repository.add(o2)

        // Now removing one owner should succeed because a replacement exists
        val removedWithReplacement = repository.removeIfReplacementExists("ws-1", "owner-1")
        assertTrue(removedWithReplacement)
        assertFalse(repository.exists("ws-1", "owner-1"))
        assertTrue(repository.exists("ws-1", "owner-2"))
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("workspace_ownership_repo")
    }
}
