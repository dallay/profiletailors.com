package com.profiletailors.smp.integration.support

import kotlinx.coroutines.reactor.awaitSingle
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.WebTestClient
import java.sql.DriverManager
import java.util.UUID

abstract class AuthorizationEndpointIntegrationTestSupport {

    @Autowired
    protected lateinit var webTestClient: WebTestClient

    @Autowired
    protected lateinit var databaseClient: DatabaseClient

    @Autowired
    protected lateinit var auditHook: CapturingAuditHook

    protected abstract fun liquibaseJdbcUrl(): String

    protected abstract fun liquibaseUsername(): String

    protected abstract fun liquibasePassword(): String

    protected open fun additionalCleanupStatements(): List<String> = emptyList()

    @BeforeEach
    fun setUpAuthorizationEndpointSupport() {
        applyLiquibaseBaseline()
        auditHook.reset()
        kotlinx.coroutines.runBlocking {
            cleanupStatements().forEach { statement ->
                databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
            }
        }
    }

    protected fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM workspace_target_scopes",
        "DELETE FROM workspace_direct_grants",
        "DELETE FROM workspace_entitlements",
        "DELETE FROM membership_roles",
        "DELETE FROM role_permissions",
        "DELETE FROM roles",
        "DELETE FROM permissions",
        "DELETE FROM workspace_memberships",
        "DELETE FROM workspace_ownerships",
        "DELETE FROM workspaces",
    ) + additionalCleanupStatements() + listOf(
        "DELETE FROM local_password_credentials",
        "DELETE FROM user_identities",
        "DELETE FROM principals",
    )

    protected suspend fun seedRolePermission(
        roleId: String = "c88a1fae-af37-5aa1-b2d4-717e9e8b478c",
        permissionId: String,
        permissionKey: String,
    ) {
        databaseClient.sql("INSERT INTO permissions (id, permission_key) VALUES (:id, :permissionKey)")
            .bind("id", UUID.fromString(permissionId))
            .bind("permissionKey", permissionKey)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            "INSERT INTO role_permissions (role_id, permission_id) VALUES (:roleId, :permissionId)",
        )
            .bind("roleId", UUID.fromString(roleId))
            .bind("permissionId", UUID.fromString(permissionId))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    protected fun applyLiquibaseBaseline() {
        DriverManager.getConnection(liquibaseJdbcUrl(), liquibaseUsername(), liquibasePassword())
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SET profiletailors.uuid_cutover_approved = 'on'")
                }
                val database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(
                        liquibase.database.jvm.JdbcConnection(connection),
                    )
                Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    ClassLoaderResourceAccessor(),
                    database,
                ).update(Contexts("uuid-native-identifiers"), LabelExpression())
            }
    }
}
