package com.profiletailors.smp.identity.application

import java.time.Instant

enum class EmailFailureCategory(val retryable: Boolean) {
    PROVIDER_UNAVAILABLE(true),
    PROVIDER_TIMEOUT(true),
    PROVIDER_REJECTED(false),
    INVALID_REQUEST(false),
}

data class EmailSendResult(
    val success: Boolean,
    val error: String? = null,
    val failureCategory: EmailFailureCategory? = null,
) {
    val retryable: Boolean
        get() = !success && failureCategory?.retryable == true

    companion object {
        fun sent(): EmailSendResult = EmailSendResult(success = true)

        fun temporaryFailure(category: EmailFailureCategory, error: String? = null): EmailSendResult {
            require(category.retryable)
            return EmailSendResult(success = false, error = error, failureCategory = category)
        }

        fun permanentFailure(category: EmailFailureCategory, error: String? = null): EmailSendResult {
            require(!category.retryable)
            return EmailSendResult(success = false, error = error, failureCategory = category)
        }
    }
}

data class PasswordResetNotificationFailure(
    val principalId: String,
    val notificationType: String,
    val attempts: Int,
    val failedAt: Instant,
    val category: EmailFailureCategory,
)

fun interface PasswordResetNotificationFailurePort {
    suspend fun record(failure: PasswordResetNotificationFailure)
}

enum class PasswordResetNotificationStatus {
    SENT,
    RETRYING,
    FAILED,
}

data class PasswordResetNotificationTelemetry(
    val notificationType: String,
    val status: PasswordResetNotificationStatus,
    val attempts: Int,
    val category: EmailFailureCategory? = null,
)

fun interface PasswordResetNotificationTelemetryPort {
    fun record(event: PasswordResetNotificationTelemetry)
}
