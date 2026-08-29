package com.profiletailors.smp.mcp.infrastructure

import org.springframework.stereotype.Component
import java.time.format.DateTimeParseException
import java.util.UUID

class McpInsufficientScopeException(val requiredScope: String) :
    RuntimeException("Insufficient scope: $requiredScope required.")

class McpPublicationNotFoundException(val publicationId: String) :
    RuntimeException("Publication '$publicationId' was not found in the active workspace.")

class McpPublicationStateConflictException(val publicationId: String, val currentStatus: String) :
    RuntimeException("Publication '$publicationId' is in status $currentStatus which does not allow this operation.")

class McpPublicationValidationFailedException(message: String) :
    IllegalArgumentException(message)

class McpMediaUnavailableException(val assetId: String) :
    RuntimeException("Media asset '$assetId' is not reachable.")

class McpIdempotencyConflictException(val toolName: String, val workspaceId: String) :
    RuntimeException("Idempotency key collision for tool '$toolName' in workspace '$workspaceId'.")

class McpRateLimitExceededException(val bucket: String, val retryAfterSeconds: Long) :
    RuntimeException("Rate limit exceeded for bucket: $bucket")

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

@Component
class McpErrorMapper {

    fun mapToError(exception: Throwable): ApplicationError {
        val correlationId = UUID.randomUUID().toString()
        return mapper(exception, correlationId)
    }

    private fun mapper(exception: Throwable, correlationId: String): ApplicationError {
        val builder = ErrorBuilder(correlationId)
        return when (exception) {
            is McpInsufficientScopeException -> auth(builder, "Token does not carry the required scope.")
            is org.springframework.security.access.AccessDeniedException -> forbidden(builder)
            is com.profiletailors.smp.publishing.application.PublicationNotFoundException,
            is McpPublicationNotFoundException -> publicationNotFound(builder)
            is McpPublicationStateConflictException -> publicationStateConflict(builder)
            is McpPublicationValidationFailedException -> publicationValidationFailed(builder, exception)
            is McpMediaUnavailableException -> mediaUnavailable(builder)
            is McpIdempotencyConflictException -> idempotencyConflict(builder)
            is McpRateLimitExceededException -> rateLimited(builder)
            is com.profiletailors.smp.publishing.application.SocialAccountNotFoundException ->
                socialAccountNotFound(builder, exception)
            is IllegalArgumentException -> illegalArgument(builder, exception)
            is IllegalStateException -> illegalState(builder, exception)
            is DateTimeParseException -> invalidDateRange(builder)
            else -> internal(builder)
        }
    }

    private fun auth(builder: ErrorBuilder, message: String): ApplicationError =
        builder.code(INSUFFICIENT_SCOPE).category(CATEGORY_AUTHORIZATION).retryable(false).message(message).build()

    private fun forbidden(builder: ErrorBuilder): ApplicationError =
        builder.code(FORBIDDEN).category(CATEGORY_AUTHORIZATION).retryable(false).message("Access denied.").build()

    private fun publicationNotFound(builder: ErrorBuilder): ApplicationError =
        builder.code(PUBLICATION_NOT_FOUND).category(CATEGORY_NOT_FOUND).retryable(false)
            .message("Publication was not found in the active workspace.").build()

    private fun publicationStateConflict(builder: ErrorBuilder): ApplicationError =
        builder.code(PUBLICATION_STATE_CONFLICT).category(CATEGORY_VALIDATION).retryable(false)
            .message("Publication is not in a state that allows this operation.").build()

    private fun publicationValidationFailed(builder: ErrorBuilder, ex: Throwable): ApplicationError =
        builder.code(PUBLICATION_VALIDATION_FAILED).category(CATEGORY_VALIDATION).retryable(false)
            .message(sanitize(ex.message ?: "Publication validation failed.")).build()

    private fun mediaUnavailable(builder: ErrorBuilder): ApplicationError =
        builder.code(MEDIA_UNAVAILABLE).category(CATEGORY_PLATFORM).retryable(true)
            .message("Referenced media is not reachable. Try again later.").build()

    private fun idempotencyConflict(builder: ErrorBuilder): ApplicationError =
        builder.code(IDEMPOTENCY_CONFLICT).category(CATEGORY_IDEMPOTENCY).retryable(false)
            .message("Idempotency key already used with a different request payload.").build()

    private fun rateLimited(builder: ErrorBuilder): ApplicationError =
        builder.code(RATE_LIMIT_EXCEEDED).category(CATEGORY_THROTTLING).retryable(true)
            .message("Too many requests. Please retry later.").build()

    private fun socialAccountNotFound(builder: ErrorBuilder, ex: Throwable): ApplicationError =
        builder.code(PUBLICATION_VALIDATION_FAILED).category(CATEGORY_VALIDATION).retryable(false)
            .message(sanitize(ex.message ?: "Social account not found.")).build()

    private fun illegalArgument(builder: ErrorBuilder, ex: Throwable): ApplicationError =
        builder.code(PUBLICATION_VALIDATION_FAILED).category(CATEGORY_VALIDATION).retryable(false)
            .message(sanitize(ex.message ?: "Invalid input.")).build()

    private fun illegalState(builder: ErrorBuilder, ex: Throwable): ApplicationError {
        val sanitized = sanitize(ex.message ?: "Operation not allowed in current state.")
        return builder.code(PUBLICATION_STATE_CONFLICT).category(CATEGORY_VALIDATION).retryable(false)
            .message(sanitized).build()
    }

    private fun invalidDateRange(builder: ErrorBuilder): ApplicationError =
        builder.code(INVALID_DATE_RANGE).category(CATEGORY_VALIDATION).retryable(false)
            .message("Invalid date format. Use ISO-8601 (e.g. 2024-01-01T00:00:00Z).").build()

    private fun internal(builder: ErrorBuilder): ApplicationError =
        builder.code(INTERNAL).category(CATEGORY_INTERNAL).retryable(true)
            .message("An internal error occurred. Please retry or contact support.").build()

    private fun auth(builder: ErrorBuilder, message: String, code: String = INSUFFICIENT_SCOPE): ApplicationError =
        builder.code(code).category(CATEGORY_AUTHORIZATION).retryable(false).message(message).build()

    private fun notFound(builder: ErrorBuilder, message: String): ApplicationError =
        builder.code(PUBLICATION_NOT_FOUND).category(CATEGORY_NOT_FOUND).retryable(false).message(message).build()

    private fun validation(
        builder: ErrorBuilder,
        message: String,
        code: String = PUBLICATION_VALIDATION_FAILED,
    ): ApplicationError =
        builder.code(code).category(CATEGORY_VALIDATION).retryable(false).message(message).build()

    private fun platform(builder: ErrorBuilder, message: String): ApplicationError =
        builder.code(MEDIA_UNAVAILABLE).category(CATEGORY_PLATFORM).retryable(true).message(message).build()

    private fun idem(builder: ErrorBuilder, message: String): ApplicationError =
        builder.code(IDEMPOTENCY_CONFLICT).category(CATEGORY_IDEMPOTENCY).retryable(false).message(message).build()

    private fun throttling(builder: ErrorBuilder, message: String): ApplicationError =
        builder.code(RATE_LIMIT_EXCEEDED).category(CATEGORY_THROTTLING).retryable(true).message(message).build()

    private fun sanitize(message: String): String =
        if (DANGEROUS_PATTERN.containsMatchIn(message)) "Invalid input." else message

    private class ErrorBuilder(private val correlationId: String) {
        private var code: String = INTERNAL
        private var category: String = CATEGORY_INTERNAL
        private var retryable: Boolean = false
        private var message: String = "Error."

        fun code(value: String) = apply { code = value }
        fun category(value: String) = apply { category = value }
        fun retryable(value: Boolean) = apply { retryable = value }
        fun message(value: String) = apply { message = value }

        fun build(): ApplicationError = ApplicationError(
            code = code,
            category = category,
            message = message,
            retryable = retryable,
            correlationId = correlationId,
        )
    }

    companion object {
        private const val INSUFFICIENT_SCOPE = "insufficient_scope"
        private const val PUBLICATION_NOT_FOUND = "publication_not_found"
        private const val PUBLICATION_STATE_CONFLICT = "publication_state_conflict"
        private const val PUBLICATION_VALIDATION_FAILED = "publication_validation_failed"
        private const val MEDIA_UNAVAILABLE = "media_unavailable"
        private const val IDEMPOTENCY_CONFLICT = "idempotency_conflict"
        private const val RATE_LIMIT_EXCEEDED = "rate_limit_exceeded"
        private const val FORBIDDEN = "forbidden"
        private const val INVALID_DATE_RANGE = "invalid_date_range"
        private const val INTERNAL = "internal"

        private const val CATEGORY_AUTHORIZATION = "authorization"
        private const val CATEGORY_NOT_FOUND = "not_found"
        private const val CATEGORY_VALIDATION = "validation"
        private const val CATEGORY_PLATFORM = "platform"
        private const val CATEGORY_IDEMPOTENCY = "idempotency"
        private const val CATEGORY_THROTTLING = "throttling"
        private const val CATEGORY_INTERNAL = "internal"

        @Suppress("StringShouldBeRawString")
        private val DANGEROUS_PATTERN = Regex(
            "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|EXEC|SQL|stack.?trace|at\\s+\\w+\\.)",
        )
    }
}
