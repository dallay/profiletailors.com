package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager

class R2dbcPrincipalIdentityLookupTest {

    private val jdbcUrl = "jdbc:h2:mem:identity_lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    private val connectionFactory = H2ConnectionFactory(
        H2ConnectionConfiguration.builder()
            .inMemory("identity_lookup")
            .property("MODE", "PostgreSQL")
            .property("DB_CLOSE_DELAY", "-1")
            .property("DB_CLOSE_ON_EXIT", "FALSE")
            .username("sa")
            .build(),
    )
    private val databaseClient = DatabaseClient.create(connectionFactory)
    private val lookup: PrincipalIdentityLookup = R2dbcPrincipalIdentityLookup(databaseClient)

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        deleteAllRows()
    }

    @Test
    fun `loads principal plus user identity facts by subject and provider`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'subject-123', 'https://issuer.example', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('principal-1', 'yuniel@example.com', 'yuniel')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val facts = lookup.findBySubject(
            principalType = PrincipalType.USER,
            subject = "subject-123",
            provider = "https://issuer.example",
        )

        requireNotNull(facts)
        assertEquals("principal-1", facts.principalId)
        assertEquals(PrincipalType.USER, facts.principalType)
        assertEquals("yuniel@example.com", facts.email)
        assertEquals("yuniel", facts.username)
        assertEquals("yuniel", facts.displayIdentity)
    }

    @Test
    fun `returns null when no principal facts exist for subject`() = runTest {
        val facts = lookup.findBySubject(
            principalType = PrincipalType.USER,
            subject = "missing-subject",
            provider = "https://issuer.example",
        )

        assertNull(facts)
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
            "DELETE FROM user_identities",
            "DELETE FROM principals",
        ).forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
    }
}
