package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import kotlinx.coroutines.async
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
        repository = R2dbcWorkspaceOwnershipRepository(databaseClient, transactionalOperator)

        // Seed some basic data
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('35205e1e-1d3d-54b7-9ea0-5d1e92734d4d', 'USER', 'owner-1', NULL, 'Owner One')
            """.trimIndent(),
        ).then().block()

        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('026cddb4-fce7-510a-9796-23002cc752b5', 'USER', 'owner-2', NULL, 'Owner Two')
            """.trimIndent(),
        ).then().block()

        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('eaee8a38-38a1-5c3c-9b23-dad13b400a27', 'Workspace Alpha', 'ACTIVE', NULL)
            """.trimIndent(),
        ).then().block()
    }

    @Test
    fun `add, exists, and findByWorkspaceId works correctly`() = runTest {
        val ownership = WorkspaceOwnership(
            workspaceId = "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            ownerPrincipalId = "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d",
            ownerPrincipalType = PrincipalType.USER,
            createdBy = "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d",
            createdAt = Instant.now(),
        )

        repository.add(ownership)

        assertTrue(repository.exists("eaee8a38-38a1-5c3c-9b23-dad13b400a27", "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d"))
        assertFalse(repository.exists("eaee8a38-38a1-5c3c-9b23-dad13b400a27", "026cddb4-fce7-510a-9796-23002cc752b5"))

        val owners = repository.findByWorkspaceId("eaee8a38-38a1-5c3c-9b23-dad13b400a27")
        assertEquals(1, owners.size)
        val firstOwner = owners.first()
        assertEquals("eaee8a38-38a1-5c3c-9b23-dad13b400a27", firstOwner.workspaceId)
        assertEquals("35205e1e-1d3d-54b7-9ea0-5d1e92734d4d", firstOwner.ownerPrincipalId)
        assertEquals(PrincipalType.USER, firstOwner.ownerPrincipalType)
    }

    @Test
    fun `removeIfReplacementExists works correctly`() = runTest {
        val o1 = WorkspaceOwnership(
            workspaceId = "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            ownerPrincipalId = "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d",
            ownerPrincipalType = PrincipalType.USER,
        )
        val o2 = WorkspaceOwnership(
            workspaceId = "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            ownerPrincipalId = "026cddb4-fce7-510a-9796-23002cc752b5",
            ownerPrincipalType = PrincipalType.USER,
        )

        repository.add(o1)

        // Attempting to remove the only owner should fail (return false)
        val removedSingle = repository.removeIfReplacementExists(
            "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d",
        )
        assertFalse(removedSingle)
        assertTrue(repository.exists("eaee8a38-38a1-5c3c-9b23-dad13b400a27", "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d"))

        // Add a second owner
        repository.add(o2)

        // Now removing one owner should succeed because a replacement exists
        val removedWithReplacement = repository.removeIfReplacementExists(
            "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d",
        )
        assertTrue(removedWithReplacement)
        assertFalse(repository.exists("eaee8a38-38a1-5c3c-9b23-dad13b400a27", "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d"))
        assertTrue(repository.exists("eaee8a38-38a1-5c3c-9b23-dad13b400a27", "026cddb4-fce7-510a-9796-23002cc752b5"))
    }

    @Test
    fun `removeIfReplacementExists maintains one-owner invariant under real concurrency`() = runTest {
        val o1 = WorkspaceOwnership(
            workspaceId = "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            ownerPrincipalId = "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d",
            ownerPrincipalType = PrincipalType.USER,
        )
        val o2 = WorkspaceOwnership(
            workspaceId = "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            ownerPrincipalId = "026cddb4-fce7-510a-9796-23002cc752b5",
            ownerPrincipalType = PrincipalType.USER,
        )

        repository.add(o1)
        repository.add(o2)

        // Launch two parallel deletions for different owners in the same workspace.
        // SELECT ... FOR UPDATE will block and serialize them correctly.
        val deferred1 = async {
            repository.removeIfReplacementExists(
                "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
                "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d",
            )
        }
        val deferred2 = async {
            repository.removeIfReplacementExists(
                "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
                "026cddb4-fce7-510a-9796-23002cc752b5",
            )
        }

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        // Exactly one deletion must succeed (return true) and one must fail (return false).
        assertTrue(result1 || result2)
        assertFalse(result1 && result2)

        // Exactly one owner must remain in the database.
        val remainingOwners = repository.findByWorkspaceId("eaee8a38-38a1-5c3c-9b23-dad13b400a27")
        assertEquals(1, remainingOwners.size)
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("workspace_ownership_repo")
    }
}
