package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.CredentialPurgeCandidate
import com.profiletailors.smp.publishing.domain.CredentialPurgeRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.r2dbc.spi.Readable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class R2dbcCredentialPurgeRepository(private val databaseClient: DatabaseClient) : CredentialPurgeRepository {
    override fun findOrphanExpiredCandidates(
        provider: SocialProvider,
        expiredBefore: Instant,
        batchSize: Int,
    ): Flow<CredentialPurgeCandidate> = databaseClient.sql(
        """
        SELECT id, provider, access_token_expires_at
        FROM secure_credentials
        WHERE provider = :provider
          AND access_token_expires_at IS NOT NULL
          AND access_token_expires_at < :expiredBefore
          AND NOT EXISTS (
              SELECT 1 FROM social_connections
              WHERE credential_reference = secure_credentials.id::text
                AND status IN ('ACTIVE', 'REQUIRES_RECONNECT')
          )
        ORDER BY access_token_expires_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """.trimIndent(),
    )
        .bind("provider", provider.name)
        .bind("expiredBefore", expiredBefore)
        .bind("batchSize", batchSize)
        .map { row, _ -> row.toPurgeCandidate() }
        .all()
        .asFlow()

    override suspend fun deleteById(id: UUID): Boolean {
        val updated = databaseClient.sql("DELETE FROM secure_credentials WHERE id = :id")
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return updated > 0
    }
}

private fun Readable.toPurgeCandidate(): CredentialPurgeCandidate = CredentialPurgeCandidate(
    id = requireNotNull(get("id", UUID::class.java)),
    provider = SocialProvider.valueOf(requireNotNull(get("provider", String::class.java))),
    accessTokenExpiresAt = get("access_token_expires_at", OffsetDateTime::class.java)?.toInstant(),
)
