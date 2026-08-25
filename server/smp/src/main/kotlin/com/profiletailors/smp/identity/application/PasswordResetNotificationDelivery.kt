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
        /**
         * Creates a successful email send result.
         *
         * @return A successful email send result.
         */
        fun sent(): EmailSendResult = EmailSendResult(success = true)

        /**
         * Creates an email send result for a retryable failure.
         *
         * @param category The retryable failure category.
         * @param error An optional error description.
         * @return An unsuccessful email send result with the specified failure details.
         * @throws IllegalArgumentException If [category] is not retryable.
         */
        fun temporaryFailure(category: EmailFailureCategory, error: String? = null): EmailSendResult {
            require(category.retryable)
            return EmailSendResult(success = false, error = error, failureCategory = category)
        }

        /**
         * Creates a result representing a permanent email delivery failure.
         *
         * @param category The non-retryable category of the failure.
         * @param error An optional error description.
         * @return An unsuccessful email send result with the specified failure details.
         * @throws IllegalArgumentException If [category] is retryable.
         */
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

fun interface PasswordResetNotificationFailureRecorder {
    /**
     * Records a failed password reset notification.
     *
     * @param failure The details of the failed notification.
     */
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

fun interface PasswordResetNotificationTelemetryRecorder {
    /**
     * Records telemetry for a password reset notification event.
     *
     * @param event The telemetry event to record.
     */
    fun record(event: PasswordResetNotificationTelemetry)
}
