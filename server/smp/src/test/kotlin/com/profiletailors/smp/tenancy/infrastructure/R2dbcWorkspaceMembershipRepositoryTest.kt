package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    @BeforeEach
    fun setUp() {
        repository = R2dbcWorkspaceMembershipRepository(databaseClient)

        // Seed some basic data
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('member-1', 'USER', 'member-1', NULL, 'Member One')
            """.trimIndent(),
        ).then().block()

        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-1', 'Workspace Alpha', 'ACTIVE', NULL)",
        ).then().block()

        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('m-1', 'ws-1', 'member-1', 'USER', 'ACTIVE')
            """.trimIndent(),
        ).then().block()
    }

    @Test
    fun `reconcile returns an existing suspended membership without changing its status`() = runTest {
        databaseClient.sql(
            "UPDATE workspace_memberships SET status = 'SUSPENDED' WHERE id = 'm-1'",
        ).then().block()

        val membership = repository.reconcile("ws-1", "member-1")

        assertEquals(WorkspaceMembershipStatus.SUSPENDED, membership.status)
        assertEquals(1, repository.findByWorkspaceId("ws-1").size)
    }

    @Test
    fun `reconcile returns existing membership without inserting a duplicate`() = runTest {
        val membership = repository.reconcile("ws-1", "member-1")

        assertEquals("m-1", membership.id)
        assertEquals(1, repository.findByWorkspaceId("ws-1").size)
    }

    @Test
    fun `reconcile inserts one active membership for a principal without one`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('member-2', 'USER', 'member-2', NULL, 'Member Two')
            """.trimIndent(),
        ).then().block()

        val membership = repository.reconcile("ws-1", "member-2")

        assertEquals("ws-1", membership.workspaceId)
        assertEquals("member-2", membership.principalId)
        assertEquals(PrincipalType.USER, membership.principalType)
        assertEquals(WorkspaceMembershipStatus.ACTIVE, membership.status)
        assertEquals(2, repository.findByWorkspaceId("ws-1").size)
    }

    @Test
    fun `findByWorkspaceId and updateStatus works correctly`() = runTest {
        val memberships = repository.findByWorkspaceId("ws-1")
        assertEquals(1, memberships.size)
        val m = memberships.first()
        assertEquals("m-1", m.id)
        assertEquals("ws-1", m.workspaceId)
        assertEquals("member-1", m.principalId)
        assertEquals(PrincipalType.USER, m.principalType)
        assertEquals(WorkspaceMembershipStatus.ACTIVE, m.status)

        // Update status
        repository.updateStatus("ws-1", "member-1", WorkspaceMembershipStatus.SUSPENDED)

        val updatedMemberships = repository.findByWorkspaceId("ws-1")
        assertEquals(WorkspaceMembershipStatus.SUSPENDED, updatedMemberships.first().status)
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("workspace_membership_repo")
    }
}
