package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.BCryptApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialCommand
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class R2dbcApiKeyCredentialReplacementGatewayTest {

    private val jdbcUrl = "jdbc:h2:mem:api_key_replacement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    private val connectionFactory = H2ConnectionFactory(
        H2ConnectionConfiguration.builder()
            .inMemory("api_key_replacement")
            .property("MODE", "PostgreSQL")
            .property("DB_CLOSE_DELAY", "-1")
            .property("DB_CLOSE_ON_EXIT", "FALSE")
            .username("sa")
            .build(),
    )
    private val databaseClient = DatabaseClient.create(connectionFactory)
    private val fixedClock = Clock.fixed(Instant.parse("2026-05-17T09:30:00Z"), ZoneOffset.UTC)
    private val gateway = R2dbcApiKeyCredentialReplacementGateway(
        connectionFactory = connectionFactory,
        secretVerifier = BCryptApiKeySecretVerifier(),
        valueFactory = StubApiKeyCredentialValueFactory(),
        clock = fixedClock,
    )

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        deleteAllRows()
    }

    @Test
    fun `replaces one active api key credential with one successor and invalidates predecessor`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(status = "ACTIVE")

        val result = gateway.replaceActiveCredential(ReplaceApiKeyCredentialCommand("api-key-cred-1"))

        assertEquals("api-key-cred-1", result.predecessorCredentialReference)
        assertEquals("api-key-cred-2", result.successorCredentialReference)
        assertEquals("ptk_successor.successor-secret", result.successorPlaintextApiKey)

        val rows = databaseClient.sql(
            """
            SELECT id, status, replaced_by_credential_id, replaced_credential_id, replaced_at, lookup_key
            FROM api_key_credentials
            ORDER BY created_at, id
            """.trimIndent(),
        ).map { row, _ ->
            mapOf(
                "id" to requireNotNull(row.get("id", String::class.java)),
                "status" to requireNotNull(row.get("status", String::class.java)),
                "replaced_by_credential_id" to row.get("replaced_by_credential_id", String::class.java),
                "replaced_credential_id" to row.get("replaced_credential_id", String::class.java),
                "replaced_at" to row.get("replaced_at", java.time.OffsetDateTime::class.java)?.toInstant(),
                "lookup_key" to requireNotNull(row.get("lookup_key", String::class.java)),
            )
        }.all().collectList().awaitSingle()

        assertEquals(2, rows.size)
        val predecessor = rows.first { it["id"] == "api-key-cred-1" }
        val successor = rows.first { it["id"] == "api-key-cred-2" }

        assertEquals("INACTIVE", predecessor["status"])
        assertEquals("api-key-cred-2", predecessor["replaced_by_credential_id"])
        assertEquals(fixedClock.instant(), predecessor["replaced_at"])
        assertEquals("ACTIVE", successor["status"])
        assertEquals("api-key-cred-1", successor["replaced_credential_id"])
        assertNull(successor["replaced_by_credential_id"])
        assertNull(successor["replaced_at"])
        assertEquals("ptk_successor", successor["lookup_key"])
    }

    @Test
    fun `rejects replacement when predecessor is already replaced`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(
            status = "ACTIVE",
            replacedAt = Instant.parse("2026-05-17T08:30:00Z"),
        )

        val error = assertThrows(ApiKeyCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                gateway.replaceActiveCredential(ReplaceApiKeyCredentialCommand("api-key-cred-1"))
            }
        }

        assertEquals(ApiKeyCredentialFailureReason.REPLACED, error.reason)
        val count = databaseClient.sql("SELECT COUNT(*) AS total FROM api_key_credentials")
            .map { row, _ -> requireNotNull(row.get("total", java.lang.Long::class.java)).toLong() }
            .one()
            .awaitSingle()
        assertEquals(1L, count)
    }

    @Test
    fun `rejects replacement when predecessor is revoked`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(status = "REVOKED", revokedAt = Instant.parse("2026-05-17T08:30:00Z"))

        val error = assertThrows(ApiKeyCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                gateway.replaceActiveCredential(ReplaceApiKeyCredentialCommand("api-key-cred-1"))
            }
        }

        assertEquals(ApiKeyCredentialFailureReason.REVOKED, error.reason)
    }

    @Test
    fun `creates successor with a distinct verifier from predecessor secret`() = runTest {
        seedPrincipal()
        seedApiKeyCredential(status = "ACTIVE")

        gateway.replaceActiveCredential(ReplaceApiKeyCredentialCommand("api-key-cred-1"))

        val verifiers = databaseClient.sql(
            "SELECT id, secret_verifier FROM api_key_credentials ORDER BY id",
        ).map { row, _ ->
            requireNotNull(row.get("id", String::class.java)) to requireNotNull(row.get("secret_verifier", String::class.java))
        }.all().collectList().awaitSingle().toMap()

        assertNotNull(verifiers["api-key-cred-1"])
        assertNotNull(verifiers["api-key-cred-2"])
        assertNotEquals(verifiers["api-key-cred-1"], verifiers["api-key-cred-2"])
    }

    private suspend fun seedPrincipal() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('api-key-principal-1', 'API_KEY', 'api-key-subject', NULL, 'integration-key')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedApiKeyCredential(
        status: String,
        revokedAt: Instant? = null,
        replacedAt: Instant? = null,
        replacedByCredentialId: String? = null,
    ) {
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
                revoked_at,
                replaced_by_credential_id,
                replaced_credential_id,
                replaced_at
            ) VALUES (
                'api-key-cred-1',
                'api-key-principal-1',
                'ptk_lookup',
                'ptk_lookup',
                :verifier,
                :status,
                :revokedAt,
                :replacedByCredentialId,
                NULL,
                :replacedAt
            )
            """.trimIndent(),
        )
            .bind("verifier", verifier)
            .bind("status", status)
            .let { spec ->
                val withRevokedAt = if (revokedAt == null) spec.bindNull("revokedAt", Instant::class.java) else spec.bind("revokedAt", revokedAt)
                val withReplacedBy = if (replacedByCredentialId == null) {
                    withRevokedAt.bindNull("replacedByCredentialId", String::class.java)
                } else {
                    withRevokedAt.bind("replacedByCredentialId", replacedByCredentialId)
                }
                if (replacedAt == null) withReplacedBy.bindNull("replacedAt", Instant::class.java) else withReplacedBy.bind("replacedAt", replacedAt)
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
        databaseClient.sql("SET REFERENTIAL_INTEGRITY FALSE").fetch().rowsUpdated().awaitSingle()
        listOf(
            "DELETE FROM api_key_credentials",
            "DELETE FROM service_account_credentials",
            "DELETE FROM user_identities",
            "DELETE FROM principals",
        ).forEach { statement ->
            databaseClient.sql(statement).fetch().rowsUpdated().awaitSingle()
        }
        databaseClient.sql("SET REFERENTIAL_INTEGRITY TRUE").fetch().rowsUpdated().awaitSingle()
    }

    private class StubApiKeyCredentialValueFactory : com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory {
        override fun nextCredentialReference(): String = "api-key-cred-2"

        override fun nextPlaintextApiKey(): com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory.PlaintextApiKey =
            com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory.PlaintextApiKey(
                lookupKey = "ptk_successor",
                keyPrefix = "ptk_successor",
                secret = "successor-secret",
            )
    }
}
