package com.profiletailors.observability

import java.util.Locale

object OperationalEventSanitizer {
    private const val REDACTED_VALUE = "[REDACTED]"
    private const val ARGUMENT_PREFIX = "argument."

    private val sensitiveKeySegments = setOf(
        "credential",
        "token",
        "password",
        "secret",
        "authorization",
        "authentication",
        "auth",
        "cookie",
        "otp",
        "email",
        "pii",
    )

    fun sanitize(event: OperationalEvent): OperationalEvent {
        val sensitiveValues = event.attributes
            .filterKeys(::isSensitiveKey)
            .values
            .filterIsInstance<String>()
            .filter(String::isNotBlank)
        val attributes = event.attributes
            .filterKeys { !isSensitiveKey(it) }
            .mapValues { (key, value) -> if (key.startsWith(ARGUMENT_PREFIX)) REDACTED_VALUE else safeValue(value) }
            .toMutableMap()

        event.cause?.let { cause ->
            attributes.putIfAbsent("errorType", cause::class.simpleName ?: "Throwable")
        }

        return event.copy(
            message = event.message?.let { message ->
                sensitiveValues.fold(message) { sanitized, sensitiveValue ->
                    sanitized.replace(sensitiveValue, REDACTED_VALUE)
                }
            },
            attributes = attributes,
            cause = null,
        )
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key
            .replace(Regex("([a-z0-9])([A-Z])"), "$1.$2")
            .lowercase(Locale.ROOT)
        val segments = normalized.split('.', '_', '-', '/', ':')
        return segments.any(sensitiveKeySegments::contains) ||
            segments.zipWithNext().any { (first, second) -> first == "api" && second == "key" }
    }

    private fun safeValue(value: Any?): Any? = when (value) {
        null,
        is Boolean,
        is Char,
        is Number,
        is String,
        is Enum<*>,
        -> value
        else -> value::class.simpleName ?: "Object"
    }
}
