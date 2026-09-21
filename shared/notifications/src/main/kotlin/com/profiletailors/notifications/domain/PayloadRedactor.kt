package com.profiletailors.notifications.domain

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
    return payload.mapValues { (key, value) -> redactEntry(key, value) }
}

fun redactSensitiveValue(value: String): String =
    if (SENSITIVE_KEY_SUBSTRINGS.any { value.lowercase().contains(it) }) REDACTED_VALUE else value

private fun redactEntry(key: String, value: Any?): Any? = when {
    isSensitiveKey(key) -> REDACTED_VALUE
    value is Map<*, *> -> redactUnknownMap(value)
    value is Iterable<*> -> value.map { item ->
        if (item is Map<*, *>) redactUnknownMap(item) else item
    }
    else -> value
}

private fun redactUnknownMap(value: Map<*, *>): Map<String, Any?> =
    value.entries.associate { (nestedKey, nestedValue) ->
        val nestedName = nestedKey?.toString().orEmpty()
        nestedName to redactEntry(nestedName, nestedValue)
    }

private fun isSensitiveKey(key: String): Boolean {
    val lowerKey = key.lowercase()
    if (ALLOWED_TOP_LEVEL_KEYS.any { lowerKey == it }) {
        return false
    }
    return SENSITIVE_KEY_SUBSTRINGS.any { lowerKey.contains(it) }
}
