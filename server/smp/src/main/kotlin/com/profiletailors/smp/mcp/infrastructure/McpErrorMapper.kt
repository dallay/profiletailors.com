package com.profiletailors.smp.mcp.infrastructure

import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Exception thrown when an MCP rate limit bucket is exhausted.
 */
class McpRateLimitExceededException(val bucket: String, val retryAfterSeconds: Long) :
    RuntimeException("Rate limit exceeded for bucket: $bucket")

/**
 * Structured error returned by MCP tools — never leaks internals.
 */
data class ApplicationError(
    val code: String,
    val category: String,
    val message: String,
    val retryable: Boolean,
    val correlationId: String,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "code" to code,
        "category" to category,
        "message" to message,
        "retryable" to retryable,
        "correlationId" to correlationId,
    )
}

/**
 * Maps domain/infrastructure exceptions to safe [ApplicationError] responses.
 *
 * Never exposes stack traces, SQL fragments, or cross-workspace identifiers.
 */
class McpErrorMapper {

    fun mapToError(exception: Throwable): ApplicationError {
        val correlationId = UUID.randomUUID().toString()

        return when (exception) {
            is McpRateLimitExceededException -> ApplicationError(
                code = "rate_limit_exceeded",
                category = "throttling",
                message = "Too many requests. Please retry later.",
                retryable = true,
                correlationId = correlationId,
            )

            is org.springframework.security.access.AccessDeniedException -> ApplicationError(
                code = "forbidden",
                category = "authorization",
                message = "Access denied.",
                retryable = false,
                correlationId = correlationId,
            )

            is DateTimeParseException -> ApplicationError(
                code = "invalid_date_range",
                category = "validation",
                message = "Invalid date format. Use ISO-8601 (e.g. 2024-01-01T00:00:00Z).",
                retryable = false,
                correlationId = correlationId,
            )

            is IllegalArgumentException -> ApplicationError(
                code = "invalid_date_range",
                category = "validation",
                message = sanitize(exception.message ?: "Invalid input."),
                retryable = false,
                correlationId = correlationId,
            )

            else -> ApplicationError(
                code = "internal",
                category = "internal",
                message = "An internal error occurred. Please retry or contact support.",
                retryable = true,
                correlationId = correlationId,
            )
        }
    }

    private fun sanitize(message: String): String {
        if (DANGEROUS_PATTERN.containsMatchIn(message)) {
            return "Invalid input."
        }
        return message
    }

    companion object {
        @Suppress("StringShouldBeRawString")
        private val DANGEROUS_PATTERN = Regex(
            "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|EXEC|SQL|stack.?trace|at\\s+\\w+\\.)",
        )
    }
}
