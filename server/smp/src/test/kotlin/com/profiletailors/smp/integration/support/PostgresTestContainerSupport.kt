package com.profiletailors.smp.integration.support

import kotlinx.coroutines.reactor.awaitSingle
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.BeforeEach
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

object PostgresTestContainerSupport {
    const val IMAGE = "postgres:16-alpine"
    const val DATABASE = "profiletailors_test"
    const val USERNAME = "profiletailors"
    const val PASSWORD = "profiletailors"

    fun newContainer(databaseName: String = DATABASE): PostgreSQLContainer<*> = PostgreSQLContainer(IMAGE)
        .withDatabaseName(databaseName)
        .withUsername(USERNAME)
        .withPassword(PASSWORD)

    fun r2dbcUrl(container: PostgreSQLContainer<*>): String =
        "r2dbc:postgresql://${container.host}:${container.getMappedPort(
            PostgreSQLContainer.POSTGRESQL_PORT,
        )}/${container.databaseName}"

    fun jdbcUrl(container: PostgreSQLContainer<*>): String = container.jdbcUrl

    fun username(container: PostgreSQLContainer<*>): String = container.username

    fun password(container: PostgreSQLContainer<*>): String = container.password

    fun registerProperties(registry: DynamicPropertyRegistry, container: PostgreSQLContainer<*>) {
        if (!container.isRunning) {
            container.start()
        }
        registry.add("spring.r2dbc.url") { r2dbcUrl(container) }
        registry.add("spring.r2dbc.username") { username(container) }
        registry.add("spring.r2dbc.password") { password(container) }
        registry.add("spring.liquibase.url") { jdbcUrl(container) }
        registry.add("spring.liquibase.user") { username(container) }
        registry.add("spring.liquibase.password") { password(container) }
    }
}

object PostgresDatabaseCleanup {
    val statements: List<String> = listOf(
        "DELETE FROM delivery_attempts",
        "DELETE FROM publication_jobs",
        "DELETE FROM publication_asset_links",
        "DELETE FROM publications",
        "DELETE FROM media_assets",
        "DELETE FROM workspace_file_blobs",
        "DELETE FROM workspace_upload_slots",
        "DELETE FROM media_rate_limits",
        "DELETE FROM publication_assets",
        "DELETE FROM social_accounts",
        "DELETE FROM social_connections",
        "DELETE FROM secure_credentials",
        "DELETE FROM audit_events",
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
        "DELETE FROM refresh_sessions",
        "DELETE FROM api_key_credentials",
        "DELETE FROM service_account_credentials",
        "DELETE FROM local_password_credentials",
        "DELETE FROM email_verification_tokens",
        "DELETE FROM user_identities",
        "DELETE FROM principals",
    )

    suspend fun clean(databaseClient: DatabaseClient) {
        statements.forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
    }
}

abstract class PostgresDatabaseTestBase {
    protected abstract val postgres: PostgreSQLContainer<*>

    protected val databaseClient: DatabaseClient by lazy {
        DatabaseClient.create(
            io.r2dbc.postgresql.PostgresqlConnectionFactory(
                io.r2dbc.postgresql.PostgresqlConnectionConfiguration.builder()
                    .host(postgres.host)
                    .port(postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                    .database(postgres.databaseName)
                    .username(postgres.username)
                    .password(postgres.password)
                    .build(),
            ),
        )
    }

    @BeforeEach
    fun setUpPostgresDatabase() {
        applyLiquibaseBaselineOnce()
        kotlinx.coroutines.runBlocking {
            PostgresDatabaseCleanup.clean(databaseClient)
        }
    }

    private val liquibaseApplied: Boolean
        get() = PostgresDatabaseTestBase.liquibaseMigrationApplied

    private fun markLiquibaseApplied() {
        PostgresDatabaseTestBase.liquibaseMigrationApplied = true
    }

    private fun applyLiquibaseBaselineOnce() {
        if (liquibaseApplied) return
        applyLiquibaseBaseline()
        markLiquibaseApplied()
    }

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).update(Contexts(), LabelExpression())
        }
    }

    companion object {
        private var liquibaseMigrationApplied = false
    }
}

abstract class PostgresIntegrationTestBase : IntegrationTestBase() {
    protected abstract val postgresContainer: PostgreSQLContainer<*>

    override fun liquibaseJdbcUrl(): String = postgresContainer.jdbcUrl
    override fun liquibaseUsername(): String = postgresContainer.username
    override fun liquibasePassword(): String = postgresContainer.password

    override fun cleanupStatements(): List<String> = PostgresDatabaseCleanup.statements
}
