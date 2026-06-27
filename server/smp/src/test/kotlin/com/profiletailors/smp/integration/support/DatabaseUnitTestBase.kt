package com.profiletailors.smp.integration.support

import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.BeforeEach
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager

/**
 * Base class for unit tests that need H2 in-memory database with Liquibase migrations.
 * Lighter than IntegrationTestBase - no Spring Boot context, no WebTestClient.
 *
 * Use for testing infrastructure components (repositories, hooks) in isolation.
 */
abstract class DatabaseUnitTestBase {

    protected abstract fun databaseName(): String

    protected val connectionFactory by lazy {
        H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory(databaseName())
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build(),
        )
    }

    protected val databaseClient by lazy {
        DatabaseClient.create(connectionFactory)
    }

    @BeforeEach
    fun setUpDatabase() {
        applyLiquibaseBaseline()
        runTest {
            cleanupTables()
        }
    }

    protected open suspend fun cleanupTables() {
        databaseClient.sql("DELETE FROM delivery_attempts").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publication_jobs").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publication_asset_links").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publications").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM media_assets").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM publication_assets").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM social_accounts").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM social_connections").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM secure_credentials").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM audit_events").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM workspace_target_scopes").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM workspace_direct_grants").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM workspace_entitlements").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM membership_roles").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM role_permissions").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM roles").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM permissions").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM workspace_memberships").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM workspace_ownerships").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM workspaces").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM refresh_sessions").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM api_key_credentials").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM service_account_credentials").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM local_password_credentials").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM email_verification_tokens").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM user_identities").fetch().rowsUpdated().awaitSingle()
        databaseClient.sql("DELETE FROM principals").fetch().rowsUpdated().awaitSingle()
    }

    private fun applyLiquibaseBaseline() {
        val dbName = databaseName()
        DriverManager.getConnection(
            "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "sa",
            "",
        ).use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).update(Contexts(), LabelExpression())
        }
    }
}
