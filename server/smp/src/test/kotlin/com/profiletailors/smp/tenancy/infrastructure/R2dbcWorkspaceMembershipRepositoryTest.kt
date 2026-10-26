package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
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
class R2dbcWorkspaceMembershipRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer
    private lateinit var repository: R2dbcWorkspaceMembershipRepository

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value).toString() == value }.getOrDefault(false)

    @BeforeEach
    fun setUp() {
        repository = R2dbcWorkspaceMembershipRepository(databaseClient)

        // Seed some basic data
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('b643233d-46a5-5ba9-afc2-3d46e412b779', 'USER', 'member-1', NULL, 'Member One')
            """.trimIndent(),
        ).then().block()

        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('eaee8a38-38a1-5c3c-9b23-dad13b400a27', 'Workspace Alpha', 'ACTIVE', NULL)
            """.trimIndent(),
        ).then().block()

        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('891d09ad-d9af-5749-970f-e5bb772f1a85', 'eaee8a38-38a1-5c3c-9b23-dad13b400a27', 'b643233d-46a5-5ba9-afc2-3d46e412b779', 'USER', 'ACTIVE')
            """.trimIndent(),
        ).then().block()
    }

    @Test
    fun `reconcile returns an existing suspended membership without changing its status`() = runTest {
        databaseClient.sql(
            "UPDATE workspace_memberships SET status = 'SUSPENDED' WHERE id = '891d09ad-d9af-5749-970f-e5bb772f1a85'",
        ).then().block()

        val membership = repository.reconcile(
            "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            "b643233d-46a5-5ba9-afc2-3d46e412b779",
        )

        assertEquals(WorkspaceMembershipStatus.SUSPENDED, membership.status)
        assertEquals(1, repository.findByWorkspaceId("eaee8a38-38a1-5c3c-9b23-dad13b400a27").size)
    }

    @Test
    fun `reconcile returns existing membership without inserting a duplicate`() = runTest {
        val membership = repository.reconcile(
            "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            "b643233d-46a5-5ba9-afc2-3d46e412b779",
        )

        assertEquals("891d09ad-d9af-5749-970f-e5bb772f1a85", membership.id)
        assertEquals(1, repository.findByWorkspaceId("eaee8a38-38a1-5c3c-9b23-dad13b400a27").size)
    }

    @Test
    fun `reconcile inserts one active membership for a principal without one`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('c46fe212-587a-5cb2-b835-1f75893da2a3', 'USER', 'member-2', NULL, 'Member Two')
            """.trimIndent(),
        ).then().block()

        val membership = repository.reconcile(
            "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            "c46fe212-587a-5cb2-b835-1f75893da2a3",
        )

        assertEquals("eaee8a38-38a1-5c3c-9b23-dad13b400a27", membership.workspaceId)
        assertEquals("c46fe212-587a-5cb2-b835-1f75893da2a3", membership.principalId)
        assertEquals(PrincipalType.USER, membership.principalType)
        assertEquals(WorkspaceMembershipStatus.ACTIVE, membership.status)
        assertTrue(isCanonicalUuid(membership.id))
        assertEquals(2, repository.findByWorkspaceId("eaee8a38-38a1-5c3c-9b23-dad13b400a27").size)
    }

    @Test
    fun `findByWorkspaceId and updateStatus works correctly`() = runTest {
        val memberships = repository.findByWorkspaceId("eaee8a38-38a1-5c3c-9b23-dad13b400a27")
        assertEquals(1, memberships.size)
        val m = memberships.first()
        assertEquals("891d09ad-d9af-5749-970f-e5bb772f1a85", m.id)
        assertEquals("eaee8a38-38a1-5c3c-9b23-dad13b400a27", m.workspaceId)
        assertEquals("b643233d-46a5-5ba9-afc2-3d46e412b779", m.principalId)
        assertEquals(PrincipalType.USER, m.principalType)
        assertEquals(WorkspaceMembershipStatus.ACTIVE, m.status)

        // Update status
        repository.updateStatus(
            "eaee8a38-38a1-5c3c-9b23-dad13b400a27",
            "b643233d-46a5-5ba9-afc2-3d46e412b779",
            WorkspaceMembershipStatus.SUSPENDED,
        )

        val updatedMemberships = repository.findByWorkspaceId("eaee8a38-38a1-5c3c-9b23-dad13b400a27")
        assertEquals(WorkspaceMembershipStatus.SUSPENDED, updatedMemberships.first().status)
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("workspace_membership_repo")
    }
}
