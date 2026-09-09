package com.profiletailors.observability

/**
 * Applies the shared safety policy before an operational event reaches a concrete adapter.
 *
 * Sensitive attributes are removed instead of replacing their values so that neither the key nor
 * the value can be accidentally rendered by a future adapter. Throwable details are represented
 * only by their simple type name; exception messages and causes are not trusted log data.
 */
object OperationalEventSanitizer {
    private const val REDACTED_VALUE = "[REDACTED]"

    private val sensitiveKeyFragments = setOf(
        "token",
        "password",
        "secret",
        "authorization",
        "auth",
        "cookie",
        "set-cookie",
        "credential",
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
            .mapValues { (_, value) -> safeValue(value) }
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
        val normalized = key.lowercase()
        return sensitiveKeyFragments.any(normalized::contains)
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
