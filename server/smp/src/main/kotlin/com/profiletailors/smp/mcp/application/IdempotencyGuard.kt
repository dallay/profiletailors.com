package com.profiletailors.smp.mcp.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.mcp.domain.IdempotencyRecord
import java.time.Instant

@Service
class IdempotencyGuard(
    private val repository: IdempotencyRecordRepository,
    private val serializer: McpJsonSerializer,
) {

    /**
     * Executes an operation once for an idempotency key and reuses its cached result on subsequent requests.
     *
     * @param workspaceId The workspace associated with the request.
     * @param principalId The principal associated with the request.
     * @param toolName The name of the requested tool.
     * @param idempotencyKey The key used to identify repeated requests.
     * @param type The class of the result type.
     * @param execute The operation to execute when no cached result exists.
     * @return The cached result or the result produced by `execute`.
     */
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
