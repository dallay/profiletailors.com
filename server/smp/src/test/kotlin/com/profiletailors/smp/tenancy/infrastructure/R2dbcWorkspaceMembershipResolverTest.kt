package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcWorkspaceMembershipResolverTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val resolver by lazy { R2dbcWorkspaceMembershipResolver(databaseClient) }

    @Test
    fun `loads active workspace membership for principal and workspace`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('f4b0c946-5bac-5e21-a5af-5213b71804cc', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('d3d9800f-120a-501e-ad01-401e54e8f766', 'Profile Tailors', 'ACTIVE', 'briefcase')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status)
            VALUES ('ae2517d0-e569-5439-a4ae-c515b532584d', 'd3d9800f-120a-501e-ad01-401e54e8f766', 'f4b0c946-5bac-5e21-a5af-5213b71804cc', 'USER', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val membership = resolver.resolve(
            principalId = "f4b0c946-5bac-5e21-a5af-5213b71804cc",
            resourceContext = ResourceContext(
                ResourceContextType.WORKSPACE,
                workspaceId = "d3d9800f-120a-501e-ad01-401e54e8f766",
            ),
        )

        requireNotNull(membership)
        assertEquals("d3d9800f-120a-501e-ad01-401e54e8f766", membership.workspaceId)
        assertEquals("f4b0c946-5bac-5e21-a5af-5213b71804cc", membership.principalId)
        assertEquals(PrincipalType.USER, membership.principalType)
    }

    @Test
    fun `returns null when no workspace membership exists`() = runTest {
        val membership = resolver.resolve(
            principalId = "f4b0c946-5bac-5e21-a5af-5213b71804cc",
            resourceContext = ResourceContext(
                ResourceContextType.WORKSPACE,
                workspaceId = "d3d9800f-120a-501e-ad01-401e54e8f766",
            ),
        )

        assertNull(membership)
    }

    private fun deleteAllRows() = runTest {
        databaseClient.sql("SET REFERENTIAL_INTEGRITY FALSE").fetch().rowsUpdated().awaitSingle()
        listOf(
            "DELETE FROM membership_roles",
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
        val postgresContainer = PostgresTestContainerSupport.newContainer("membership_lookup")
    }
}
