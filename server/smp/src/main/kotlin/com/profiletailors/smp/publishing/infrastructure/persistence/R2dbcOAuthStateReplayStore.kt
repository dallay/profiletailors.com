package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.OAuthStateReplayStore
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class R2dbcOAuthStateReplayStore(private val databaseClient: DatabaseClient, private val clock: Clock) :
    OAuthStateReplayStore {
    override suspend fun consume(nonce: String, expiresAt: Instant): Boolean {
        val now = clock.instant()
        if (!expiresAt.isAfter(now)) return false
        databaseClient.sql("DELETE FROM publishing_oauth_consumed_states WHERE expires_at <= :now")
            .bind("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
            .fetch().rowsUpdated().awaitSingle()
        return databaseClient.sql(
            """
            INSERT INTO publishing_oauth_consumed_states (nonce, expires_at)
            VALUES (:nonce, :expiresAt)
            ON CONFLICT (nonce) DO NOTHING
            """.trimIndent(),
        )
            .bind("nonce", nonce)
            .bind("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
            .fetch().rowsUpdated().awaitSingle() == 1L
    }
}
