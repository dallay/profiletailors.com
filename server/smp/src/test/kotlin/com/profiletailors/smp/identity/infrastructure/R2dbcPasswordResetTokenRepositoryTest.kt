package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordResetCredentialMissingException
import com.profiletailors.smp.identity.application.PasswordResetTokenCleanup
import com.profiletailors.smp.identity.application.PasswordResetTokenRepository
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcPasswordResetTokenRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var repository: PasswordResetTokenRepository

    @BeforeEach
    fun setUpRepository() {
        repository = R2dbcPasswordResetTokenRepository(databaseClient)
    }

    @Test
    fun `create inserts a token and findByTokenHash locates it`() = runTest {
        seedPrincipal()

        val tokenHash = "hash-one"
        repository.create(
            principalId = "user-1",
            tokenHash = tokenHash,
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )

        val stored = repository.findByTokenHash(tokenHash)
        assertNotNull(stored)
        assertEquals("user-1", stored?.principalId)
        assertEquals(tokenHash, stored?.tokenHash)
        assertEquals(Instant.parse("2026-07-27T12:30:00Z"), stored?.expiresAt)
        assertNull(stored?.usedAt)
    }

    @Test
    fun `schema references user identities with compatible principal id width`() = runTest {
        val metadata = databaseClient.sql(
            """
            SELECT ccu.table_name AS target_table,
                   ccu.column_name AS target_column,
                   cols.character_maximum_length AS principal_length
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu
              ON ccu.constraint_name = tc.constraint_name
            JOIN information_schema.columns cols
              ON cols.table_name = 'password_reset_tokens' AND cols.column_name = 'principal_id'
            WHERE tc.constraint_name = 'fk_password_reset_principal'
            """.trimIndent(),
        ).map { row, _ ->
            Triple(
                row.get("target_table", String::class.java),
                row.get("target_column", String::class.java),
                row.get("principal_length", Long::class.javaObjectType),
            )
        }.one().awaitSingle()

        assertEquals(Triple("user_identities", "principal_id", 64L), metadata)
    }

    @Test
    fun `findByTokenHash returns null for unknown hash`() = runTest {
        assertNull(repository.findByTokenHash("not-stored"))
    }

    @Test
    fun `findForConsumption locates an existing token`() = runTest {
        seedPrincipal()

        val tokenHash = "for-consumption-hash"
        repository.create(
            principalId = "user-1",
            tokenHash = tokenHash,
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )

        val stored = repository.findForConsumption(tokenHash)
        assertNotNull(stored)
        assertEquals("user-1", stored?.principalId)
        assertEquals(tokenHash, stored?.tokenHash)
    }

    @Test
    fun `findForConsumption returns null for unknown hash`() = runTest {
        assertNull(repository.findForConsumption("not-stored"))
    }

    @Test
    fun `create rejects duplicate token hashes`() = runTest {
        seedPrincipal()

        repository.create(
            principalId = "user-1",
            tokenHash = "duplicate-hash",
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )

        val exception = runCatching {
            repository.create(
                principalId = "user-1",
                tokenHash = "duplicate-hash",
                requestedAt = Instant.parse("2026-07-27T12:05:00Z"),
                expiresAt = Instant.parse("2026-07-27T12:35:00Z"),
            )
        }.exceptionOrNull()

        assertNotNull(exception, "Expected a uniqueness-violation error")
    }

    @Test
    fun `invalidateActiveTokens marks only unused tokens as used`() = runTest {
        seedPrincipal()

        repository.create(
            principalId = "user-1",
            tokenHash = "active-1",
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )
        repository.create(
            principalId = "user-1",
            tokenHash = "expired-unused",
            requestedAt = Instant.parse("2026-07-27T11:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T11:30:00Z"),
        )
        repository.create(
            principalId = "user-1",
            tokenHash = "active-2",
            requestedAt = Instant.parse("2026-07-27T12:01:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:31:00Z"),
        )
        repository.create(
            principalId = "user-1",
            tokenHash = "already-used",
            requestedAt = Instant.parse("2026-07-27T11:30:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:00:00Z"),
        )
        markTokenUsed("already-used", Instant.parse("2026-07-27T11:45:00Z"))

        repository.invalidateActiveTokens("user-1", Instant.parse("2026-07-27T12:10:00Z"))

        val active1 = repository.findByTokenHash("active-1")
        val active2 = repository.findByTokenHash("active-2")
        val used = repository.findByTokenHash("already-used")
        val expiredUnused = repository.findByTokenHash("expired-unused")
        assertNotNull(active1?.usedAt)
        assertNotNull(active2?.usedAt)
        assertNull(expiredUnused?.usedAt)
        assertEquals(Instant.parse("2026-07-27T11:45:00Z"), used?.usedAt)
    }

    @Test
    fun `consumeAndUpdatePassword updates the password hash and marks the token as used on success`() = runTest {
        seedPrincipal()
        seedLocalPasswordCredential("user-1", "old-hash")

        val tokenHash = "consume-hash"
        repository.create(
            principalId = "user-1",
            tokenHash = tokenHash,
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )

        repository.consumeAndUpdatePassword(
            tokenHash = tokenHash,
            now = Instant.parse("2026-07-27T12:10:00Z"),
            newPasswordHash = "new-hash",
        )

        val stored = repository.findByTokenHash(tokenHash)
        assertEquals(Instant.parse("2026-07-27T12:10:00Z"), stored?.usedAt)
        assertEquals("new-hash", lookupPasswordHash("user-1"))
    }

    @Test
    fun `consumeAndUpdatePassword throws for expired token`() = runTest {
        seedPrincipal()
        seedLocalPasswordCredential("user-1", "old-hash")

        val tokenHash = "expired-hash"
        repository.create(
            principalId = "user-1",
            tokenHash = tokenHash,
            requestedAt = Instant.parse("2026-07-27T11:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T11:30:00Z"),
        )

        shouldThrow<PasswordResetCredentialMissingException> {
            repository.consumeAndUpdatePassword(
                tokenHash = tokenHash,
                now = Instant.parse("2026-07-27T12:00:00Z"),
                newPasswordHash = "new-hash",
            )
        }
        assertNull(repository.findByTokenHash(tokenHash)?.usedAt)
        assertEquals("old-hash", lookupPasswordHash("user-1"))
    }

    @Test
    fun `consumeAndUpdatePassword throws for already-used token`() = runTest {
        seedPrincipal()
        seedLocalPasswordCredential("user-1", "old-hash")

        val tokenHash = "used-hash"
        repository.create(
            principalId = "user-1",
            tokenHash = tokenHash,
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )
        markTokenUsed(tokenHash, Instant.parse("2026-07-27T12:05:00Z"))

        shouldThrow<PasswordResetCredentialMissingException> {
            repository.consumeAndUpdatePassword(
                tokenHash = tokenHash,
                now = Instant.parse("2026-07-27T12:10:00Z"),
                newPasswordHash = "new-hash",
            )
        }
        assertEquals("old-hash", lookupPasswordHash("user-1"))
    }

    @Test
    fun `consumeAndUpdatePassword throws for unknown hash`() = runTest {
        seedPrincipal()
        seedLocalPasswordCredential("user-1", "old-hash")

        shouldThrow<PasswordResetCredentialMissingException> {
            repository.consumeAndUpdatePassword(
                tokenHash = "unknown-hash",
                now = Instant.parse("2026-07-27T12:10:00Z"),
                newPasswordHash = "new-hash",
            )
        }
        assertEquals("old-hash", lookupPasswordHash("user-1"))
    }

    @Test
    fun `concurrent consumeAndUpdatePassword calls allow exactly one success`() = runTest {
        seedPrincipal()
        seedLocalPasswordCredential("user-1", "old-hash")

        val tokenHash = "concurrent-hash"
        repository.create(
            principalId = "user-1",
            tokenHash = tokenHash,
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )

        val now = Instant.parse("2026-07-27T12:10:00Z")
        val outcomes = run {
            val first = async {
                runCatching {
                    repository.consumeAndUpdatePassword(tokenHash, now, "first-new-hash")
                }
            }
            val second = async {
                runCatching {
                    repository.consumeAndUpdatePassword(tokenHash, now, "second-new-hash")
                }
            }
            awaitAll(first, second)
        }

        val successes = outcomes.count { it.isSuccess }
        assertEquals(1, successes, "Exactly one concurrent call should succeed")

        val storedHash = lookupPasswordHash("user-1")
        assertTrue(
            storedHash == "first-new-hash" || storedHash == "second-new-hash",
            "Stored hash must match one of the call sites, got $storedHash",
        )
        assertNotNull(repository.findByTokenHash(tokenHash)?.usedAt)
    }

    @Test
    fun `cleanup deletes only tokens expired before the retention cutoff and is idempotent`() = runTest {
        seedPrincipal()
        val tokenCleanup: PasswordResetTokenCleanup = R2dbcPasswordResetTokenRepository(databaseClient)
        val cutoff = Instant.parse("2026-07-20T12:00:00Z")

        repository.create("user-1", "expired-old", cutoff.minusSeconds(3600), cutoff.minusSeconds(1))
        repository.create("user-1", "expired-at-cutoff", cutoff.minusSeconds(3600), cutoff)
        repository.create("user-1", "expired-recent", cutoff.minusSeconds(1800), cutoff.plusSeconds(1))
        repository.create("user-1", "active", cutoff, cutoff.plusSeconds(86_400))
        repository.create("user-1", "recently-used", cutoff.minusSeconds(7200), cutoff.minusSeconds(1))
        markTokenUsed("recently-used", cutoff)

        assertEquals(1L, tokenCleanup.deleteExpiredBefore(cutoff))
        assertNull(repository.findByTokenHash("expired-old"))
        assertNotNull(repository.findByTokenHash("expired-at-cutoff"))
        assertNotNull(repository.findByTokenHash("expired-recent"))
        assertNotNull(repository.findByTokenHash("active"))
        assertNotNull(repository.findByTokenHash("recently-used"))

        assertEquals(0L, tokenCleanup.deleteExpiredBefore(cutoff))
    }

    @Test
    fun `deleting principal cascades to password_reset_tokens`() = runTest {
        seedPrincipal()
        repository.create(
            principalId = "user-1",
            tokenHash = "cascade-hash",
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )

        databaseClient.sql("DELETE FROM user_identities WHERE principal_id = :id")
            .bind("id", "user-1")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql("DELETE FROM principals WHERE id = :id")
            .bind("id", "user-1")
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        assertNull(repository.findByTokenHash("cascade-hash"))
    }

    @Test
    fun `lookup never returns a record when supplied with the raw token`() = runTest {
        seedPrincipal()
        val rawToken = "raw-token-that-is-not-the-hash"
        // Generate a real hash for the raw token but only store the hash
        val hash = sha256Hex(rawToken)
        repository.create(
            principalId = "user-1",
            tokenHash = hash,
            requestedAt = Instant.parse("2026-07-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-07-27T12:30:00Z"),
        )

        // Searching with the raw token (not its hash) must NOT match
        assertNull(repository.findByTokenHash(rawToken))
        assertNotNull(repository.findByTokenHash(hash))
    }

    private suspend fun seedPrincipal() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('user-1', 'USER', 'local:user@example.com', NULL, 'user')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO user_identities (principal_id, email, username)
            VALUES ('user-1', 'user@example.com', 'user')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun seedLocalPasswordCredential(principalId: String, passwordHash: String) {
        databaseClient.sql(
            """
            INSERT INTO local_password_credentials (principal_id, password_hash)
            VALUES (:id, :hash)
            """.trimIndent(),
        )
            .bind("id", principalId)
            .bind("hash", passwordHash)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun markTokenUsed(tokenHash: String, usedAt: Instant) {
        databaseClient.sql(
            """
            UPDATE password_reset_tokens
            SET used_at = :usedAt
            WHERE token_hash = :tokenHash
            """.trimIndent(),
        )
            .bind("usedAt", usedAt)
            .bind("tokenHash", tokenHash)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun lookupPasswordHash(principalId: String): String? = databaseClient.sql(
        """
        SELECT password_hash FROM local_password_credentials WHERE principal_id = :id
        """.trimIndent(),
    )
        .bind("id", principalId)
        .map { row, _ -> requireNotNull(row.get("password_hash", String::class.java)) }
        .one()
        .awaitSingle()

    private fun sha256Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("password_reset_repo")
    }
}
