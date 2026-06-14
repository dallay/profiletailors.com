package com.profiletailors.smp.authorization.infrastructure

import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.infrastructure.persistence.R2dbcWorkspaceMembershipRoleResolver
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager

class R2dbcWorkspaceMembershipRoleResolverTest {

    private val jdbcUrl = "jdbc:h2:mem:role_lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    private val connectionFactory = H2ConnectionFactory(
        H2ConnectionConfiguration.builder()
            .inMemory("role_lookup")
            .property("MODE", "PostgreSQL")
            .property("DB_CLOSE_DELAY", "-1")
            .property("DB_CLOSE_ON_EXIT", "FALSE")
            .username("sa")
            .build(),
    )
    private val databaseClient = DatabaseClient.create(connectionFactory)
    private val resolver = R2dbcWorkspaceMembershipRoleResolver(databaseClient)

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        deleteAllRows()
    }

    @Test
    fun `loads effective role permissions for membership`() = runTest {
        databaseClient.sql("INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspaces (id, name, status, icon) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE', NULL)").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) VALUES ('membership-1', 'workspace-1', 'principal-1', 'USER', 'ACTIVE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES ('permission-1', 'workspace:access:read')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO roles (id, role_key, category) VALUES ('role-1', 'member', 'WORKSPACE')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO role_permissions (role_id, permission_id) VALUES ('role-1', 'permission-1')").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("INSERT INTO membership_roles (membership_id, role_id) VALUES ('membership-1', 'role-1')").fetch().rowsUpdated().awaitSingle()

        val facts = resolver.resolve(
            com.profiletailors.smp.tenancy.domain.WorkspaceMembership(
                id = "membership-1",
                workspaceId = "workspace-1",
                principalId = "principal-1",
                principalType = com.profiletailors.common.domain.context.PrincipalType.USER,
                status = com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.ACTIVE,
            ),
        )

        assertEquals(1, facts.size)
        val role = facts.first()
        assertEquals("member", role.key)
        assertTrue(role.permissions.contains(PermissionKey.of("workspace", "access", "read")))
    }

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).update(Contexts(), LabelExpression())
        }
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
}
