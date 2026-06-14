package com.profiletailors.smp.integration.support

import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.sql.DriverManager
import java.time.Instant

/**
 * Base class for Spring Boot integration tests with H2 in-memory database.
 * Provides shared infrastructure for Liquibase migrations, database cleanup,
 * JWT authentication, and common seed data patterns.
 *
 * Subclasses must:
 * - Define their own `@SpringBootTest` annotation with unique database name
 * - Import `IntegrationTestBase.SharedTestConfiguration`
 * - Override `databaseName()` to return the H2 database name
 * - Override `seedScenario()` to populate test-specific data
 */
abstract class IntegrationTestBase {

    companion object {
        const val API_V1_MEDIA_TYPE = "application/vnd.api.v1+json"
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var databaseClient: DatabaseClient

    @Autowired
    lateinit var auditHook: CapturingAuditHook

    /**
     * Returns the database name for this test class.
     * For H2 tests this should match the in-memory database name in @SpringBootTest properties.
     */
    protected abstract fun databaseName(): String

    protected open fun liquibaseJdbcUrl(): String =
        "jdbc:h2:mem:${databaseName()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"

    protected open fun liquibaseUsername(): String = "sa"

    protected open fun liquibasePassword(): String = ""

    /**
     * Seeds test-specific scenario data after cleanup.
     * Called automatically in setUp().
     */
    protected abstract suspend fun seedScenario()

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        auditHook.reset()
        kotlinx.coroutines.runBlocking {
            cleanupStatements().forEach { statement ->
                databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
            }
            seedScenario()
        }
    }

    protected suspend fun seedPrincipal(principalId: String) {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity) 
            VALUES (:id, 'USER', :subject, 'https://issuer.example', :displayIdentity)
            """.trimIndent()
        )
            .bind("id", principalId)
            .bind("subject", "subject-$principalId")
            .bind("displayIdentity", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    protected suspend fun seedUserIdentity(principalId: String, email: String, username: String) {
        databaseClient.sql(
            "INSERT INTO user_identities (principal_id, email, username) VALUES (:principalId, :email, :username)"
        )
            .bind("principalId", principalId)
            .bind("email", email)
            .bind("username", username)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    protected suspend fun seedWorkspace(workspaceId: String, name: String, status: String = "ACTIVE", icon: String? = null) {
        databaseClient.sql(
            "INSERT INTO workspaces (id, name, status, icon) VALUES (:id, :name, :status, :icon)"
        )
            .bind("id", workspaceId)
            .bind("name", name)
            .bind("status", status)
            .let { spec ->
                if (icon == null) spec.bindNull("icon", String::class.java)
                else spec.bind("icon", icon)
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    protected suspend fun seedWorkspaceMembership(
        id: String,
        workspaceId: String,
        principalId: String,
        principalType: String = "USER",
        status: String = "ACTIVE"
    ) {
        databaseClient.sql(
            """
            INSERT INTO workspace_memberships (id, workspace_id, principal_id, principal_type, status) 
            VALUES (:id, :workspaceId, :principalId, :principalType, :status)
            """.trimIndent()
        )
            .bind("id", id)
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalId)
            .bind("principalType", principalType)
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    protected suspend fun seedWorkspaceOwnership(
        workspaceId: String,
        ownerPrincipalId: String,
        ownerPrincipalType: String = "USER",
        createdBy: String
    ) {
        databaseClient.sql(
            """
            INSERT INTO workspace_ownerships (workspace_id, owner_principal_id, owner_principal_type, created_by) 
            VALUES (:workspaceId, :ownerPrincipalId, :ownerPrincipalType, :createdBy)
            """.trimIndent()
        )
            .bind("workspaceId", workspaceId)
            .bind("ownerPrincipalId", ownerPrincipalId)
            .bind("ownerPrincipalType", ownerPrincipalType)
            .bind("createdBy", createdBy)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    protected open fun cleanupStatements(): List<String> = listOf(
        "DELETE FROM delivery_attempts",
        "DELETE FROM publication_jobs",
        "DELETE FROM publication_asset_links",
        "DELETE FROM publications",
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
        "DELETE FROM email_verification_tokens",
        "DELETE FROM user_identities",
        "DELETE FROM principals",
    )

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(
            liquibaseJdbcUrl(),
            liquibaseUsername(),
            liquibasePassword(),
        ).use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database
            ).update(Contexts(), LabelExpression())
        }
    }

    @TestConfiguration
    class SharedTestConfiguration {
        @Bean
        @Primary
        fun testAuditHook(): CapturingAuditHook = CapturingAuditHook()

        @Bean
        fun reactiveJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
            when (token) {
                "owner-token" -> Mono.just(
                    Jwt.withTokenValue(token)
                        .subject("subject-owner-1")
                        .header("alg", "none")
                        .claim("sub", "subject-owner-1")
                        .claim("iss", "https://issuer.example")
                        .claim("principal_id", "owner-1")
                        .claim("principal_type", "USER")
                        .issuedAt(Instant.parse("2026-05-20T10:15:30Z"))
                        .expiresAt(Instant.parse("2026-05-20T11:15:30Z"))
                        .build()
                )
                else -> Mono.error(BadJwtException("Invalid token"))
            }
        }

        /**
         * Creates H2 ConnectionFactory with the given database name.
         * Subclasses should override this bean with @Primary if they need a different database name.
         */
        @Bean
        fun connectionFactory(): ConnectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory("default_test_db")
                .property("MODE", "PostgreSQL")
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "FALSE")
                .username("sa")
                .build()
        )
    }
}
