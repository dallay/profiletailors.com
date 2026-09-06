package com.profiletailors.smp.administrative.domain

import java.time.Instant
import java.util.UUID

private val SENSITIVE_SUBSTRINGS = listOf(
    "password",
    "token",
    "secret",
    "credential",
    "key",
    "invitationtoken",
    "resettoken",
    "refreshtoken",
    "accesstoken",
)

fun redact(metadata: Map<String, String>?): Map<String, String> = metadata?.filterKeys { key ->
    SENSITIVE_SUBSTRINGS.none { substring -> key.lowercase().contains(substring) }
} ?: emptyMap()

data class AdministrativeAuditEvent(
    val id: UUID,
    val actorId: UUID,
    val actorType: String,
    val action: String,
    val targetId: String,
    val targetType: String,
    val correlationId: String?,
    val metadata: Map<String, String>,
    val occurredAt: Instant,
) {
    init {
        require(actorType.isNotBlank()) { "actorType must not be blank" }
        require(action.isNotBlank()) { "action must not be blank" }
        require(targetType.isNotBlank()) { "targetType must not be blank" }
        require(targetId.isNotBlank()) { "targetId must not be blank" }
        require(
            metadata.keys.none { key ->
                SENSITIVE_SUBSTRINGS.any { substring -> key.lowercase().contains(substring) }
            },
        ) { "metadata must not contain sensitive keys" }
    }
}
