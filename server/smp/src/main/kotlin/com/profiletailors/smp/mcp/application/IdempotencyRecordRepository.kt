package com.profiletailors.smp.mcp.application

import com.profiletailors.smp.mcp.domain.IdempotencyRecord

interface IdempotencyRecordRepository {

    suspend fun find(workspaceId: String, principalId: String, toolName: String, keyHash: String): String?

    suspend fun save(record: IdempotencyRecord): IdempotencyRecord
}
