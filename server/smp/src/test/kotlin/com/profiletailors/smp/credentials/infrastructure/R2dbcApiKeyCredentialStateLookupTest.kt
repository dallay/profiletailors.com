package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup
import com.profiletailors.smp.credentials.application.BCryptApiKeySecretVerifier
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager
import java.time.Instant

class R2dbcApiKeyCredentialStateLookupTest {

    private val jdbcUrl = "jdbc:h2:mem:api_key_lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    private val connectionFactory = H2ConnectionFactory(
        H2ConnectionConfiguration.builder()
            .inMemory("api_key_lookup")
            .property("MODE", "PostgreSQL")
            .property("DB_CLOSE_DELAY", "-1")
            .property("DB_CLOSE_ON_EXIT", "FALSE")
            .username("sa")
            .build(),
    )
    private val databaseClient = DatabaseClient.create(connectionFactory)
    private val lookup: ApiKeyCredentialStateLookup = R2dbcApiKeyCredentialStateLookup(
        databaseClient = databaseClient,
        secretVerifier = BCryptApiKeySecretVerifier(),
    )

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        deleteAllRows()
    }

    @Test
    fun `loads active api key credential when lookup key and secret are valid`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(status = "ACTIVE")

        val activeCredential = lookup.requireActive("ptk_lookup.secret-value")

        assertEquals("api-key-principal-1", activeCredential.principalId)
        assertEquals("api-key-cred-1", activeCredential.credentialReference)
        assertEquals("api-key-subject", activeCredential.subject)
        assertEquals(null, activeCredential.provider)
    }

    @Test
    fun `rejects api key when stored credential is revoked`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(status = "REVOKED")

        val error = assertThrows(ApiKeyCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                lookup.requireActive("ptk_lookup.secret-value")
            }
        }

        assertEquals(ApiKeyCredentialFailureReason.REVOKED, error.reason)
        assertEquals("api-key-principal-1", error.principalId)
    }

    @Test
    fun `rejects api key when stored credential is inactive`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(status = "INACTIVE")

        val error = assertThrows(ApiKeyCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                lookup.requireActive("ptk_lookup.secret-value")
            }
        }

        assertEquals(ApiKeyCredentialFailureReason.INACTIVE, error.reason)
        assertEquals("api-key-principal-1", error.principalId)
    }

    @Test
    fun `rejects api key when secret verifier does not match`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(status = "ACTIVE")

        val error = assertThrows(ApiKeyCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                lookup.requireActive("ptk_lookup.wrong-secret")
            }
        }

        assertEquals(ApiKeyCredentialFailureReason.INVALID, error.reason)
    }

    private suspend fun seedPrincipal() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('api-key-principal-1', 'API_KEY', 'api-key-subject', NULL, 'integration-key')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedApiKeyCredential(status: String) {
        val verifier = BCryptApiKeySecretVerifier().hash("secret-value")
        databaseClient.sql(
            """
            INSERT INTO api_key_credentials (
                id,
                principal_id,
                lookup_key,
                key_prefix,
                secret_verifier,
                status,
                revoked_at
            ) VALUES (
                'api-key-cred-1',
                'api-key-principal-1',
                'ptk_lookup',
                'ptk_lookup',
                :verifier,
                :status,
                :revokedAt
            )
            """.trimIndent(),
        )
            .bind("verifier", verifier)
            .bind("status", status)
            .let { spec ->
                if (status == "REVOKED") spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z")) else spec.bindNull("revokedAt", Instant::class.java)
            }
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
            "DELETE FROM api_key_credentials",
            "DELETE FROM service_account_credentials",
            "DELETE FROM user_identities",
            "DELETE FROM principals",
        ).forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
    }
}
