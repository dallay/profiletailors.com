package com.profiletailors.smp.mcp.domain

import com.profiletailors.common.domain.ValueObject
import java.time.Instant

@ValueObject
data class IdempotencyRecord(
    val id: Long? = null,
    val workspaceId: String,
    val principalId: String,
    val toolName: String,
    val keyHash: String,
    val responseJson: String,
    val createdAt: Instant = Instant.now(),
) {
    init {
        require(workspaceId.isNotBlank()) { "Workspace ID is required." }
        require(principalId.isNotBlank()) { "Principal ID is required." }
        require(toolName.isNotBlank()) { "Tool name is required." }
        require(keyHash.length == SHA256_HEX_LENGTH) { "Key hash must be $SHA256_HEX_LENGTH hex chars (SHA-256)." }
        require(responseJson.isNotBlank()) { "Response JSON is required." }
    }

    companion object {
        const val SHA256_HEX_LENGTH = 64
    }
}
