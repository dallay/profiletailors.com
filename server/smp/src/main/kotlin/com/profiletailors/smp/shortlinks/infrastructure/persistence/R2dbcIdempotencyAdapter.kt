package com.profiletailors.smp.shortlinks.infrastructure.persistence

import com.profiletailors.smp.shortlinks.domain.IdempotencyPort
import com.profiletailors.smp.shortlinks.domain.IdempotencyRecord
import com.profiletailors.smp.shortlinks.domain.OwnerId
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcIdempotencyAdapter(private val databaseClient: DatabaseClient) : IdempotencyPort {
    override suspend fun findStoredResult(key: String, ownerId: OwnerId): IdempotencyRecord? = databaseClient.sql(
        "SELECT payload_hash, result_json FROM shortlink_idempotency " +
            "WHERE owner_id = :ownerId AND idempotency_key = :key",
    )
        .bind("ownerId", ownerId.value)
        .bind("key", key)
        .map { row, _ ->
            IdempotencyRecord(
                payloadHash = requireNotNull(row.get("payload_hash", String::class.java)),
                resultJson = requireNotNull(row.get("result_json", String::class.java)),
            )
        }
        .one()
        .awaitSingleOrNull()

    override suspend fun claim(key: String, ownerId: OwnerId, payloadHash: String): Boolean = databaseClient.sql(
        "INSERT INTO shortlink_idempotency (owner_id, idempotency_key, payload_hash, result_json) " +
            "VALUES (:ownerId, :key, :payloadHash, '') ON CONFLICT (owner_id, idempotency_key) DO NOTHING",
    )
        .bind("ownerId", ownerId.value)
        .bind("key", key)
        .bind("payloadHash", payloadHash)
        .fetch()
        .rowsUpdated()
        .awaitSingleOrNull() == 1L

    override suspend fun store(key: String, ownerId: OwnerId, payloadHash: String, resultJson: String) {
        databaseClient.sql(
            "UPDATE shortlink_idempotency SET result_json = :resultJson " +
                "WHERE owner_id = :ownerId AND idempotency_key = :key AND payload_hash = :payloadHash",
        )
            .bind("ownerId", ownerId.value)
            .bind("key", key)
            .bind("payloadHash", payloadHash)
            .bind("resultJson", resultJson)
            .fetch()
            .rowsUpdated()
            .awaitSingleOrNull()
    }
}
