package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ActiveRefreshSession
import com.profiletailors.smp.credentials.application.CreatedRefreshSession
import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import com.profiletailors.smp.credentials.application.RefreshSessionStatus
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.application.RefreshTokenHasher
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class R2dbcRefreshSessionGateway(
    private val databaseClient: DatabaseClient,
    private val refreshTokenHasher: RefreshTokenHasher,
) : RefreshSessionGateway {

    override suspend fun create(
        principalId: String,
        refreshToken: RefreshSessionToken,
        expiresAt: Instant,
    ): CreatedRefreshSession {
        val sessionId = "refresh-session-${UUID.randomUUID()}"
        databaseClient.sql(
            """
            INSERT INTO refresh_sessions (
                id,
                principal_id,
                lookup_key,
                token_verifier,
                status,
                expires_at
            ) VALUES (
                :id,
                :principalId,
                :lookupKey,
                :tokenVerifier,
                :status,
                :expiresAt
            )
            """.trimIndent(),
        )
            .bind("id", sessionId)
            .bind("principalId", principalId)
            .bind("lookupKey", refreshToken.lookupKey)
            .bind("tokenVerifier", refreshTokenHasher.hash(refreshToken.secret))
            .bind("status", RefreshSessionStatus.ACTIVE.name)
            .bind("expiresAt", expiresAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return CreatedRefreshSession(
            id = sessionId,
            principalId = principalId,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
        )
    }

    override suspend fun requireActive(refreshToken: RefreshSessionToken, now: Instant): ActiveRefreshSession {
        val record = lookup(refreshToken.lookupKey)

        val failureReason = when {
            record.status == RefreshSessionStatus.REVOKED -> RefreshSessionFailureReason.REVOKED

            record.status == RefreshSessionStatus.ROTATED -> RefreshSessionFailureReason.ROTATED

            record.expiresAt.isBefore(now) -> RefreshSessionFailureReason.EXPIRED

            !refreshTokenHasher.matches(refreshToken.secret, record.tokenVerifier) ->
                RefreshSessionFailureReason.INVALID

            else -> null
        }

        if (failureReason != null) {
            throw RefreshSessionNotActiveException(
                lookupKey = refreshToken.lookupKey,
                principalId = record.principalId,
                reason = failureReason,
            )
        }

        return ActiveRefreshSession(
            id = record.id,
            principalId = record.principalId,
            lookupKey = record.lookupKey,
            tokenVerifier = record.tokenVerifier,
            expiresAt = record.expiresAt,
            createdAt = record.createdAt,
            lastUsedAt = record.lastUsedAt,
        )
    }

    override suspend fun rotate(
        currentSessionId: String,
        replacementToken: RefreshSessionToken,
        expiresAt: Instant,
        now: Instant,
    ): CreatedRefreshSession {
        val replacement = create(resolvePrincipalId(currentSessionId), replacementToken, expiresAt)

        databaseClient.sql(
            """
            UPDATE refresh_sessions
            SET status = :status,
                rotated_at = :rotatedAt,
                last_used_at = :lastUsedAt,
                replaced_by_session_id = :replacementSessionId
            WHERE id = :currentSessionId
            """.trimIndent(),
        )
            .bind("status", RefreshSessionStatus.ROTATED.name)
            .bind("rotatedAt", now)
            .bind("lastUsedAt", now)
            .bind("replacementSessionId", replacement.id)
            .bind("currentSessionId", currentSessionId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        databaseClient.sql(
            """
            UPDATE refresh_sessions
            SET replaced_session_id = :currentSessionId,
                last_used_at = :lastUsedAt
            WHERE id = :replacementSessionId
            """.trimIndent(),
        )
            .bind("currentSessionId", currentSessionId)
            .bind("lastUsedAt", now)
            .bind("replacementSessionId", replacement.id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return replacement
    }

    /**
     * Revokes a refresh session.
     *
     * @param currentSessionId The identifier of the session to revoke.
     * @param now The timestamp to record for the revocation and last use.
     */
    override suspend fun revoke(currentSessionId: String, now: Instant) {
        databaseClient.sql(
            """
            UPDATE refresh_sessions
            SET status = :status,
                revoked_at = :revokedAt,
                last_used_at = :lastUsedAt
            WHERE id = :currentSessionId
            """.trimIndent(),
        )
            .bind("status", RefreshSessionStatus.REVOKED.name)
            .bind("revokedAt", now)
            .bind("lastUsedAt", now)
            .bind("currentSessionId", currentSessionId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
         * Resolves the principal associated with a refresh session.
         *
         * @param sessionId The refresh session identifier.
         * @return The identifier of the associated principal.
         * @throws IllegalStateException If the refresh session does not exist.
         * @throws NullPointerException If the stored principal identifier is null.
         */
        private suspend fun resolvePrincipalId(sessionId: String): String = databaseClient.sql(
        "SELECT principal_id FROM refresh_sessions WHERE id = :id",
    )
        .bind("id", sessionId)
        .map { row, _ -> requireNotNull(row.get("principal_id", String::class.java)) }
        .one()
        .awaitSingleOrNull()
        ?: error("Refresh session '$sessionId' was not found while rotating.")

    private suspend fun lookup(lookupKey: String): RefreshSessionRecord = databaseClient.sql(
        """
        SELECT id,
               principal_id,
               lookup_key,
               token_verifier,
               status,
               expires_at,
               created_at,
               last_used_at
        FROM refresh_sessions
        WHERE lookup_key = :lookupKey
        """.trimIndent(),
    )
        .bind("lookupKey", lookupKey)
        .map { row, _ ->
            RefreshSessionRecord(
                id = requireNotNull(row.get("id", String::class.java)),
                principalId = requireNotNull(row.get("principal_id", String::class.java)),
                lookupKey = requireNotNull(row.get("lookup_key", String::class.java)),
                tokenVerifier = requireNotNull(row.get("token_verifier", String::class.java)),
                status = RefreshSessionStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
                expiresAt = requireNotNull(row.get("expires_at", OffsetDateTime::class.java)).toInstant(),
                createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
                lastUsedAt = row.get("last_used_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
        .one()
        .awaitSingleOrNull()
        ?: throw RefreshSessionNotActiveException(
            lookupKey = lookupKey,
            reason = RefreshSessionFailureReason.MISSING,
        )

    private data class RefreshSessionRecord(
        val id: String,
        val principalId: String,
        val lookupKey: String,
        val tokenVerifier: String,
        val status: RefreshSessionStatus,
        val expiresAt: Instant,
        val createdAt: Instant,
        val lastUsedAt: Instant?,
    )
}
