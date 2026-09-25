package com.profiletailors.smp.leadcapture.infrastructure.notification

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
internal class R2dbcWaitlistWithdrawalUrlRepository(private val databaseClient: DatabaseClient) :
    WaitlistWithdrawalUrlRepository {
    override suspend fun save(
        entryId: WaitlistEntryId,
        ciphertextVersion: String,
        ciphertext: String,
        expiresAt: Instant,
    ) {
        databaseClient.sql(
            """
            INSERT INTO waitlist_withdrawal_urls (entry_id, ciphertext_version, ciphertext, expires_at)
            VALUES (:entryId, :ciphertextVersion, :ciphertext, :expiresAt)
            ON CONFLICT (entry_id) DO UPDATE SET
                ciphertext_version = EXCLUDED.ciphertext_version,
                ciphertext = EXCLUDED.ciphertext,
                expires_at = EXCLUDED.expires_at,
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
        )
            .bind("entryId", entryId.value)
            .bind("ciphertextVersion", ciphertextVersion)
            .bind("ciphertext", ciphertext)
            .bind("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun findByEntryId(entryId: WaitlistEntryId): WaitlistWithdrawalUrlRepository.StoredWithdrawalUrl? =
        databaseClient.sql(
            """
            SELECT ciphertext_version, ciphertext, expires_at
            FROM waitlist_withdrawal_urls
            WHERE entry_id = :entryId
            """.trimIndent(),
        )
            .bind("entryId", entryId.value)
            .map { row, _ ->
                WaitlistWithdrawalUrlRepository.StoredWithdrawalUrl(
                    ciphertextVersion = requireNotNull(row.get("ciphertext_version", String::class.java)),
                    ciphertext = requireNotNull(row.get("ciphertext", String::class.java)),
                    expiresAt = requireNotNull(row.get("expires_at", OffsetDateTime::class.java)).toInstant(),
                )
            }
            .one()
            .awaitSingleOrNull()

    override suspend fun deleteByEntryId(entryId: WaitlistEntryId) {
        databaseClient.sql("DELETE FROM waitlist_withdrawal_urls WHERE entry_id = :entryId")
            .bind("entryId", entryId.value)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
