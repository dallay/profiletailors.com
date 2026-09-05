package com.profiletailors.smp.mcp.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.mcp.domain.IdempotencyRecord
import java.time.Instant

@Service
class IdempotencyGuard(
    private val repository: IdempotencyRecordRepository,
    private val serializer: McpJsonSerializer,
) {

    suspend fun <T> guard(
        workspaceId: String,
        principalId: String,
        toolName: String,
        idempotencyKey: String?,
        type: Class<T>,
        execute: suspend () -> T,
    ): T {
        if (idempotencyKey.isNullOrBlank()) {
            return execute()
        }

        val keyHash = IdempotencyKeyHasher.hash(idempotencyKey)
        val cached = repository.find(workspaceId, principalId, toolName, keyHash)
        if (cached != null) {
            return serializer.fromJson(cached, type)
        }

        val result = execute()
        repository.save(
            IdempotencyRecord(
                workspaceId = workspaceId,
                principalId = principalId,
                toolName = toolName,
                keyHash = keyHash,
                responseJson = serializer.toJson(result),
                createdAt = Instant.now(),
            ),
        )
        return result
    }
}
