package com.profiletailors.smp.platformadmin.infrastructure.util

private const val REDACTED_VALUE = "[REDACTED]"

private val SENSITIVE_KEY_SUBSTRINGS = setOf(
    "token",
    "password",
    "accepturl",
    "rawtoken",
    "verificationtoken",
    "secret",
    "credential",
    "privatekey",
    "apikey",
)

private val ALLOWED_TOP_LEVEL_KEYS = setOf(
    "id",
    "channel",
    "templateId",
    "template_id",
    "recipient",
    "status",
    "error",
    "createdAt",
    "created_at",
    "sentAt",
    "sent_at",
    "failedAt",
    "failed_at",
    "updatedAt",
    "updated_at",
    "errorMessage",
    "error_message",
)

fun redactPayload(payload: Map<String, Any?>): Map<String, Any?> {
    if (payload.isEmpty()) {
        return emptyMap()
    }
    return payload.mapValues { (key, value) ->
        when {
            isSensitiveKey(key) -> REDACTED_VALUE
            value is Map<*, *> ->
                @Suppress("UNCHECKED_CAST")
                redactPayload(value as Map<String, Any?>)
            else -> value
        }
    }
}

private fun isSensitiveKey(key: String): Boolean {
    val lowerKey = key.lowercase()
    if (ALLOWED_TOP_LEVEL_KEYS.any { lowerKey == it }) {
        return false
    }
    return SENSITIVE_KEY_SUBSTRINGS.any { lowerKey.contains(it) }
}
