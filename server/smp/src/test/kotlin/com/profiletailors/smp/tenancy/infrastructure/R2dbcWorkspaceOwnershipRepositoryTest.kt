package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.identity.domain.PrincipalType
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
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager
import java.time.Instant

class R2dbcWorkspaceOwnershipRepositoryTest {

    private val jdbcUrl = "jdbc:h2:mem:ownership_lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    private val connectionFactory = H2ConnectionFactory(
        H2ConnectionConfiguration.builder()
            .inMemory("ownership_lookup")
            .property("MODE", "PostgreSQL")
            .property("DB_CLOSE_DELAY", "-1")
            .property("DB_CLOSE_ON_EXIT", "FALSE")
            .username("sa")
            .build(),
    )
    private val databaseClient = DatabaseClient.create(connectionFactory)
    private val repository = R2dbcWorkspaceOwnershipRepository(databaseClient)

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        deleteAllRows()
    }

    @Test
    fun `stores and loads multiple owners per workspace`() = runTest {
        seedPrincipal("owner-1")
        seedPrincipal("owner-2")
        seedPrincipal("creator-1")
        seedWorkspace()

        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
                createdBy = "creator-1",
                createdAt = Instant.parse("2026-05-20T10:15:30Z"),
            ),
        )
        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-2",
                ownerPrincipalType = PrincipalType.USER,
                createdBy = "creator-1",
                createdAt = Instant.parse("2026-05-20T10:16:30Z"),
            ),
        )

        val owners = repository.findByWorkspaceId("workspace-1")

        assertEquals(2, owners.size)
        assertTrue(owners.any { it.ownerPrincipalId == "owner-1" && it.createdBy == "creator-1" })
        assertTrue(owners.any { it.ownerPrincipalId == "owner-2" && it.createdBy == "creator-1" })
    }

    @Test
    fun `removes a specific owner without affecting others`() = runTest {
        seedPrincipal("owner-1")
        seedPrincipal("owner-2")
        seedWorkspace()
        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        repository.add(
            com.profiletailors.smp.tenancy.domain.WorkspaceOwnership(
                workspaceId = "workspace-1",
                ownerPrincipalId = "owner-2",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )

        repository.remove("workspace-1", "owner-1")

        val owners = repository.findByWorkspaceId("workspace-1")
        assertEquals(setOf("owner-2"), owners.map { it.ownerPrincipalId }.toSet())
    }

    private suspend fun seedPrincipal(principalId: String) {
        databaseClient.sql(
            "INSERT INTO principals (id, principal_type, subject, provider, display_identity) VALUES (:id, 'USER', :subject, 'https://issuer.example', :displayIdentity)",
        )
            .bind("id", principalId)
            .bind("subject", "subject-$principalId")
            .bind("displayIdentity", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedWorkspace() {
        databaseClient.sql("INSERT INTO workspaces (id, name, status) VALUES ('workspace-1', 'Profile Tailors', 'ACTIVE')")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
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
        listOf(
            "DELETE FROM workspace_memberships",
            "DELETE FROM workspace_ownerships",
            "DELETE FROM workspaces",
            "DELETE FROM user_identities",
            "DELETE FROM principals",
        ).forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
    }
}
