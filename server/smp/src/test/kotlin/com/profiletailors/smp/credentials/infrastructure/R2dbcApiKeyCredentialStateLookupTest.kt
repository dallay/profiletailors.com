package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup
import com.profiletailors.smp.credentials.infrastructure.BCryptApiKeySecretVerifier
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcApiKeyCredentialStateLookupTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val lookup: ApiKeyCredentialStateLookup by lazy {
        R2dbcApiKeyCredentialStateLookup(
            databaseClient = databaseClient,
            secretVerifier = BCryptApiKeySecretVerifier(),
        )
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
                if (status ==
                    "REVOKED"
                ) {
                    spec.bind("revokedAt", Instant.parse("2026-05-15T10:45:30Z"))
                } else {
                    spec.bindNull("revokedAt", Instant::class.java)
                }
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
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

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("api_key_lookup")
    }
}
