package com.profiletailors.smp.mcp.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class IdempotencyRecordTest {
    @Test
    fun `record retains UUID primary key`() {
        val id = UUID.fromString("fbc7d123-e943-4667-9ba9-2c26bf33d011")
        val record = IdempotencyRecord(
            id = id,
            workspaceId = "workspace",
            principalId = "principal",
            toolName = "publish",
            keyHash = "a".repeat(IdempotencyRecord.SHA256_HEX_LENGTH),
            responseJson = "{}",
            createdAt = Instant.parse("2026-10-25T12:00:00Z"),
        )

        assertEquals(id, record.id)
    }
}
