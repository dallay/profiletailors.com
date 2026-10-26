package com.profiletailors.smp.authorization.infrastructure

import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.infrastructure.persistence.R2dbcWorkspaceMembershipRoleResolver
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
class R2dbcWorkspaceMembershipRoleResolverTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val resolver by lazy { R2dbcWorkspaceMembershipRoleResolver(databaseClient) }

    @Test
    fun `loads effective role permissions for membership`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('f4b0c946-5bac-5e21-a5af-5213b71804cc', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('d3d9800f-120a-501e-ad01-401e54e8f766', 'Profile Tailors', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('ae2517d0-e569-5439-a4ae-c515b532584d', 'd3d9800f-120a-501e-ad01-401e54e8f766', 'f4b0c946-5bac-5e21-a5af-5213b71804cc', 'USER', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO permissions (id, permission_key)
            VALUES ('60461c36-6137-55f3-89ef-3d2fd2123af8', 'workspace:access:read')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO roles (id, role_key, category)
            VALUES ('c88a1fae-af37-5aa1-b2d4-717e9e8b478c', 'member', 'WORKSPACE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES ('c88a1fae-af37-5aa1-b2d4-717e9e8b478c', '60461c36-6137-55f3-89ef-3d2fd2123af8')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO membership_roles (membership_id, role_id)
            VALUES ('ae2517d0-e569-5439-a4ae-c515b532584d', 'c88a1fae-af37-5aa1-b2d4-717e9e8b478c')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val facts = resolver.resolve(
            com.profiletailors.smp.tenancy.domain.WorkspaceMembership(
                id = "ae2517d0-e569-5439-a4ae-c515b532584d",
                workspaceId = "d3d9800f-120a-501e-ad01-401e54e8f766",
                principalId = "f4b0c946-5bac-5e21-a5af-5213b71804cc",
                principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                status = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.ACTIVE,
            ),
        )

        assertEquals(1, facts.size)
        val role = facts.first()
        assertEquals("member", role.key)
        assertTrue(role.permissions.contains(PermissionKey.of("workspace", "access", "read")))
    }

    private fun deleteAllRows() = runTest {
        databaseClient.sql("SET REFERENTIAL_INTEGRITY FALSE").fetch().rowsUpdated().awaitSingle()
        listOf(
            "DELETE FROM membership_roles",
            "DELETE FROM role_permissions",
            "DELETE FROM roles",
            "DELETE FROM permissions",
            "DELETE FROM workspace_memberships",
            "DELETE FROM workspace_ownerships",
            "DELETE FROM workspaces",
            "DELETE FROM user_identities",
            "DELETE FROM principals",
        ).forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
        databaseClient.sql("SET REFERENTIAL_INTEGRITY TRUE").fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("role_lookup")
    }
}
