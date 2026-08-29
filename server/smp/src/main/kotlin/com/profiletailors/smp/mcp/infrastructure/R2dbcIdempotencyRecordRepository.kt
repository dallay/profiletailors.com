package com.profiletailors.smp.mcp.infrastructure

import com.profiletailors.smp.mcp.application.IdempotencyRecordRepository
import com.profiletailors.smp.mcp.domain.IdempotencyRecord
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant

private const val RESPONSE_JSON_COLUMN = "response_json"

@Repository
internal class R2dbcIdempotencyRecordRepository(private val databaseClient: DatabaseClient) :
    IdempotencyRecordRepository {

    override suspend fun find(
        workspaceId: String,
        principalId: String,
        toolName: String,
        keyHash: String,
    ): String? = databaseClient.sql(
        """
        SELECT response_json FROM idempotency_records
        WHERE workspace_id = :workspaceId
          AND principal_id = :principalId
          AND tool_name = :toolName
          AND key_hash = :keyHash
        """.trimIndent(),
    )
        .bind("workspaceId", workspaceId)
        .bind("principalId", principalId)
        .bind("toolName", toolName)
        .bind("keyHash", keyHash)
        .map { row, _ -> requireNotNull(row.get(RESPONSE_JSON_COLUMN, String::class.java)) }
        .one()
        .awaitSingleOrNull()

    override suspend fun save(record: IdempotencyRecord): IdempotencyRecord {
        val now: Instant = record.createdAt
        databaseClient.sql(
            """
            INSERT INTO idempotency_records
                (workspace_id, principal_id, tool_name, key_hash, response_json, created_at)
            VALUES
                (:workspaceId, :principalId, :toolName, :keyHash, :responseJson, :createdAt)
            """.trimIndent(),
        )
            .bind("workspaceId", record.workspaceId)
            .bind("principalId", record.principalId)
            .bind("toolName", record.toolName)
            .bind("keyHash", record.keyHash)
            .bind("responseJson", record.responseJson)
            .bind("createdAt", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return record
    }
}
