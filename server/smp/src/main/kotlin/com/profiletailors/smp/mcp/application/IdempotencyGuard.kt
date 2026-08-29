package com.profiletailors.smp.mcp.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.common.domain.Service
import com.profiletailors.smp.mcp.domain.IdempotencyRecord
import java.time.Instant

@Service
class IdempotencyGuard(
    private val repository: IdempotencyRecordRepository,
    private val objectMapper: ObjectMapper = IdempotencyGuard.defaultObjectMapper(),
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
            return objectMapper.readValue(cached, type)
        }

        val result = execute()
        repository.save(
            IdempotencyRecord(
                workspaceId = workspaceId,
                principalId = principalId,
                toolName = toolName,
                keyHash = keyHash,
                responseJson = objectMapper.writeValueAsString(result),
                createdAt = Instant.now(),
            ),
        )
        return result
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
