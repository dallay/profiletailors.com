package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordResetCredentialMissingException
import com.profiletailors.smp.identity.application.PasswordResetTokenCleanup
import com.profiletailors.smp.identity.application.PasswordResetTokenRepository
import com.profiletailors.smp.identity.domain.PasswordResetToken
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class R2dbcPasswordResetTokenRepository(private val databaseClient: DatabaseClient) :
    PasswordResetTokenRepository,
    PasswordResetTokenCleanup {

    /**
     * Deletes password reset tokens that expired before the specified cutoff.
     *
     * @param cutoff The expiration threshold for eligible tokens.
     * @return The number of deleted tokens.
     */
    override suspend fun deleteExpiredBefore(cutoff: Instant): Long = databaseClient.sql(
        "DELETE FROM password_reset_tokens WHERE expires_at < :cutoff AND (used_at IS NULL OR used_at < :cutoff)",
    )
        .bind("cutoff", cutoff)
        .fetch()
        .rowsUpdated()
        .awaitSingle()

    /**
     * Creates a password reset token record.
     *
     * @param principalId The identifier of the principal associated with the token.
     * @param tokenHash The hashed password reset token.
     * @param requestedAt The time the reset token was requested.
     * @param expiresAt The time the reset token expires.
     */
    override suspend fun create(principalId: String, tokenHash: String, requestedAt: Instant, expiresAt: Instant) {
        databaseClient.sql(
            """
            INSERT INTO password_reset_tokens (
                id, principal_id, token_hash, requested_at, expires_at, used_at
            ) VALUES (
                :id, :principalId, :tokenHash, :requestedAt, :expiresAt, NULL
            )
            """.trimIndent(),
        )
            .bind("id", UUID.randomUUID())
            .bind("principalId", principalId)
            .bind(TOKEN_HASH_BIND, tokenHash)
            .bind("requestedAt", requestedAt)
            .bind("expiresAt", expiresAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun invalidateActiveTokens(principalId: String, invalidatedAt: Instant) {
        databaseClient.sql("SELECT pg_advisory_xact_lock(hashtext(:principalId))")
            .bind("principalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        databaseClient.sql(
            """
            UPDATE password_reset_tokens
            SET used_at = :invalidatedAt
            WHERE principal_id = :principalId
              AND used_at IS NULL
              AND expires_at > :invalidatedAt
            """.trimIndent(),
        )
            .bind("invalidatedAt", invalidatedAt)
            .bind("principalId", principalId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
         * Finds a password reset token by its hash.
         *
         * @param tokenHash The hashed password reset token to find.
         * @return The matching password reset token, or `null` if no token exists.
         */
        override suspend fun findByTokenHash(tokenHash: String): PasswordResetToken? = databaseClient.sql(
        """
        SELECT id, principal_id, token_hash, requested_at, expires_at, used_at
        FROM password_reset_tokens
        WHERE token_hash = :tokenHash
        """.trimIndent(),
    )
        .bind(TOKEN_HASH_BIND, tokenHash)
        .map { row, _ ->
            PasswordResetToken(
                id = requireNotNull(row.get("id", UUID::class.java)),
                principalId = requireNotNull(row.get("principal_id", String::class.java)),
                tokenHash = requireNotNull(row.get("token_hash", String::class.java)),
                requestedAt = requireNotNull(row.get("requested_at", OffsetDateTime::class.java)).toInstant(),
                expiresAt = requireNotNull(row.get("expires_at", OffsetDateTime::class.java)).toInstant(),
                usedAt = row.get("used_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
        .one()
        .awaitSingleOrNull()

    /**
         * Finds a password-reset token by hash while locking the matching row for update.
         *
         * @param tokenHash The hashed password-reset token to find.
         * @return The matching password-reset token, or `null` if no token exists.
         */
        override suspend fun findForConsumption(tokenHash: String): PasswordResetToken? = databaseClient.sql(
        """
        SELECT id, principal_id, token_hash, requested_at, expires_at, used_at
        FROM password_reset_tokens
        WHERE token_hash = :tokenHash
        FOR UPDATE
        """.trimIndent(),
    )
        .bind(TOKEN_HASH_BIND, tokenHash)
        .map { row, _ ->
            PasswordResetToken(
                id = requireNotNull(row.get("id", UUID::class.java)),
                principalId = requireNotNull(row.get("principal_id", String::class.java)),
                tokenHash = requireNotNull(row.get("token_hash", String::class.java)),
                requestedAt = requireNotNull(row.get("requested_at", OffsetDateTime::class.java)).toInstant(),
                expiresAt = requireNotNull(row.get("expires_at", OffsetDateTime::class.java)).toInstant(),
                usedAt = row.get("used_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
        .one()
        .awaitSingleOrNull()

    /**
     * Atomic consume-and-update.
     *
     * The first UPDATE returns 0 rows iff the token is unknown, expired, or
     * already consumed. Any subsequent state changes (the password credential
     * UPDATE and the caller-supplied session revocation) execute only when the
     * first UPDATE consumed exactly one row. If the surrounding transaction
     * rolls back, no state changes persist. The password UPDATE MUST also
     * affect exactly one row; otherwise the call throws
     * [com.profiletailors.smp.identity.application.PasswordResetCredentialMissingException]
     * to force the transaction to roll back.
     */
    override suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String) {
        val consumed: Long = databaseClient.sql(
            """
            UPDATE password_reset_tokens
            SET used_at = :now
            WHERE token_hash = :tokenHash
              AND used_at IS NULL
              AND expires_at > :now
            """.trimIndent(),
        )
            .bind("now", now)
            .bind(TOKEN_HASH_BIND, tokenHash)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        if (consumed != 1L) {
            throw PasswordResetCredentialMissingException()
        }

        val passwordUpdated: Long = databaseClient.sql(
            """
            UPDATE local_password_credentials
            SET password_hash = :newPasswordHash,
                updated_at = :now
            WHERE principal_id = (
                SELECT principal_id FROM password_reset_tokens WHERE token_hash = :tokenHash
            )
            """.trimIndent(),
        )
            .bind("newPasswordHash", newPasswordHash)
            .bind("now", now)
            .bind(TOKEN_HASH_BIND, tokenHash)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        if (passwordUpdated != 1L) {
            throw PasswordResetCredentialMissingException()
        }
    }
}

private const val TOKEN_HASH_BIND = "tokenHash"
