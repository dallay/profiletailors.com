package com.profiletailors.smp.integration.support

import kotlinx.coroutines.reactor.awaitSingle
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.BeforeEach
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import java.sql.DriverManager
import java.time.Duration

object PostgresTestContainerSupport {
    const val IMAGE = "postgres:18-alpine"
    const val DATABASE = "profiletailors_test"
    const val USERNAME = "profiletailors"
    const val DEFAULT_PASSWORD = "profiletailors-test"

    fun newContainer(databaseName: String = DATABASE, password: String = DEFAULT_PASSWORD): PostgreSQLContainer<*> =
        PostgreSQLContainer(IMAGE)
            .withDatabaseName(databaseName)
            .withUsername(USERNAME)
            .withPassword(password)
            .waitingFor(
                WaitAllStrategy()
                    .withStrategy(Wait.forListeningPort())
                    .withStartupTimeout(Duration.ofSeconds(120)),
            )

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
        "DELETE FROM bulk_import_rows",
        "DELETE FROM bulk_import_jobs",
        "DELETE FROM hashtag_saved_sets",
        "DELETE FROM ideas",
        "DELETE FROM idea_board_configs",
        "DELETE FROM platform_admin_audit_events",
        "DELETE FROM invitations",
        "DELETE FROM waitlist_invitations",
        "DELETE FROM platform_role_assignments",
        "DELETE FROM waitlist_entries",
        "DELETE FROM waitlists WHERE id <> 'profile-tailors-launch'",
        "DELETE FROM recurring_schedules",
        "DELETE FROM delivery_attempts",
        "DELETE FROM publication_jobs",
        "DELETE FROM publication_asset_links",
        "DELETE FROM publications",
        "DELETE FROM media_assets",
        "DELETE FROM workspace_file_blobs",
        "DELETE FROM workspace_upload_slots",
        "DELETE FROM media_rate_limits",
        "DELETE FROM publication_assets",
        "DELETE FROM social_content_reply_commands",
        "DELETE FROM social_content_webhook_events",
        "DELETE FROM social_content_sync_checkpoints",
        "DELETE FROM social_content_payload_cache",
        "DELETE FROM social_content_comments",
        "DELETE FROM social_content_posts",
        "DELETE FROM social_content_actor_capabilities",
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

    protected val connectionFactory by lazy {
        io.r2dbc.postgresql.PostgresqlConnectionFactory(
            io.r2dbc.postgresql.PostgresqlConnectionConfiguration.builder()
                .host(postgres.host)
                .port(postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(postgres.databaseName)
                .username(postgres.username)
                .password(postgres.password)
                .build(),
        )
    }

    protected val databaseClient: DatabaseClient by lazy {
        DatabaseClient.create(connectionFactory)
    }

    protected val transactionalOperator by lazy {
        TransactionalOperator.create(R2dbcTransactionManager(connectionFactory))
    }

    @BeforeEach
    fun setUpPostgresDatabase() {
        applyLiquibaseBaselineOnce()
        kotlinx.coroutines.runBlocking {
            PostgresDatabaseCleanup.clean(databaseClient)
        }
    }

    private fun applyLiquibaseBaselineOnce() {
        val key = postgres.jdbcUrl
        if (!liquibaseTracker.shouldApply(key)) return
        applyLiquibaseBaseline()
        liquibaseTracker.markApplied(key)
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
        // Each test class has its own PostgreSQL container with a distinct database name.
        // The tracker keys on the JDBC URL so every database gets its Liquibase baseline
        // applied exactly once, regardless of which test class runs first.
        private val liquibaseTracker = LiquibaseBaselineTracker()
    }
}

/**
 * Tracks which PostgreSQL databases have already had the Liquibase baseline applied.
 * Each test class points at a distinct database (see `newContainer` in
 * `PostgresTestContainerSupport`), so a single Boolean flag would silently skip
 * Liquibase on the second and subsequent databases and leave them empty.
 */
class LiquibaseBaselineTracker {
    private val applied = mutableSetOf<String>()

    @Synchronized
    fun shouldApply(jdbcUrl: String): Boolean = jdbcUrl !in applied

    @Synchronized
    fun markApplied(jdbcUrl: String) {
        applied.add(jdbcUrl)
    }

    @Synchronized
    fun reset() {
        applied.clear()
    }
}

abstract class PostgresIntegrationTestBase : IntegrationTestBase() {
    protected abstract val postgresContainer: PostgreSQLContainer<*>

    override fun liquibaseJdbcUrl(): String = postgresContainer.jdbcUrl
    override fun liquibaseUsername(): String = postgresContainer.username
    override fun liquibasePassword(): String = postgresContainer.password

    override fun cleanupStatements(): List<String> = PostgresDatabaseCleanup.statements
}

/**
 * Counts the number of publication job rows for a given publication.
 * Extracted from duplicated private copies across publishing integration tests.
 */
suspend fun org.springframework.r2dbc.core.DatabaseClient.countPublicationJobs(publicationId: String): Long = sql(
    "SELECT COUNT(*) AS count FROM publication_jobs WHERE publication_id = :publicationId",
)
    .bind("publicationId", publicationId)
    .map { row, _ -> requireNotNull(row.get("count", Long::class.javaObjectType)) }
    .one()
    .awaitSingle()
