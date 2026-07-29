package com.profiletailors.smp.identity.infrastructure.observability

import com.profiletailors.smp.identity.application.PasswordResetNotificationStatus
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetry
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetryPort
import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component

@Component
class PasswordRecoveryObservabilityAdapter(
    private val meterRegistry: MeterRegistry,
    private val observationRegistry: ObservationRegistry,
) : PasswordResetNotificationTelemetryPort {

    /**
     * Records the outcome of a password reset notification delivery.
     *
     * @param event The notification telemetry event to record.
     */
    override fun record(event: PasswordResetNotificationTelemetry) {
        record(
            outcome = PasswordRecoveryOutcome(
                operation = "notification_delivery",
                notificationType = event.notificationType.safeNotificationType(),
                status = event.status.safeStatus(),
                failureCategory = event.category?.name?.lowercase() ?: "none",
                attemptBucket = event.attemptBucket(),
            ),
        )
    }

    /**
     * Records a completed password recovery reset outcome.
     */
    fun recordResetCompleted() {
        record(resetOutcome(status = "completed"))
    }

    /**
     * Records a failed password recovery reset with the specified failure category.
     *
     * @param category The category describing why the password recovery reset failed.
     */
    fun recordResetFailed(category: PasswordResetFailureCategory) {
        record(resetOutcome(status = "failed", failureCategory = category.value))
    }

    fun recordCleanupDeleted(deleted: Long) {
        if (deleted <= 0) return
        Counter.builder(CLEANUP_METRIC_NAME)
            .description("Password reset token cleanup aggregate counts")
            .register(meterRegistry)
            .increment(deleted.toDouble())
    }

    /**
     * Records a password recovery outcome as a metric and observation.
     *
     * @param outcome The password recovery outcome to record.
     */
    private fun record(outcome: PasswordRecoveryOutcome) {
        val tags = outcome.tags()
        Counter.builder(METRIC_NAME)
            .description("Password recovery reset and notification outcomes")
            .tags(tags)
            .register(meterRegistry)
            .increment()

        val observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
        observation.lowCardinalityKeyValues(
            KeyValues.of(tags.map { tag -> KeyValue.of(tag.key, tag.value) }),
        )
        observation.start()
        observation.stop()
    }

    /**
     * Creates a telemetry outcome for a password reset operation.
     *
     * @param status The reset outcome status.
     * @param failureCategory The failure category, or `"none"` when no failure occurred.
     * @return The password recovery outcome for the reset operation.
     */
    private fun resetOutcome(status: String, failureCategory: String = "none") = PasswordRecoveryOutcome(
        operation = "reset",
        notificationType = "none",
        status = status,
        failureCategory = failureCategory,
        attemptBucket = "none",
    )

    /**
     * Maps a notification type to its standardized telemetry value.
     *
     * @return `password_reset` for the password reset type, or `unknown` for other values.
     */
    private fun String.safeNotificationType(): String = when (this) {
        "PASSWORD_RESET" -> "password_reset"
        else -> "unknown"
    }

    /**
     * Maps a password reset notification status to its telemetry label.
     *
     * @return The standardized status label.
     */
    private fun PasswordResetNotificationStatus.safeStatus(): String = when (this) {
        PasswordResetNotificationStatus.SENT -> "success"
        PasswordResetNotificationStatus.RETRYING -> "retry"
        PasswordResetNotificationStatus.FAILED -> "terminal_failure"
    }

    /**
     * Categorizes the notification attempt based on its status and attempt count.
     *
     * @return The attempt bucket: `"first"`, `"retry"`, or `"exhausted"`.
     */
    private fun PasswordResetNotificationTelemetry.attemptBucket(): String = when (status) {
        PasswordResetNotificationStatus.SENT -> if (attempts == 1) "first" else "retry"
        PasswordResetNotificationStatus.RETRYING -> "retry"
        PasswordResetNotificationStatus.FAILED -> if (attempts == 1) "first" else "exhausted"
    }

    private data class PasswordRecoveryOutcome(
        val operation: String,
        val notificationType: String,
        val status: String,
        val failureCategory: String,
        val attemptBucket: String,
    ) {
        /**
         * Builds metric tags describing the password recovery outcome.
         *
         * @return The outcome's operation, notification type, status, failure category, and attempt bucket tags.
         */
        fun tags(): List<Tag> = listOf(
            Tag.of("operation", operation),
            Tag.of("notification.type", notificationType),
            Tag.of("status", status),
            Tag.of("failure.category", failureCategory),
            Tag.of("attempt.bucket", attemptBucket),
        )
    }

    private companion object {
        const val METRIC_NAME = "identity.password.recovery.outcomes"
        const val OBSERVATION_NAME = "identity.password.recovery"
        const val CLEANUP_METRIC_NAME = "identity.password.recovery.cleanup.deleted"
    }
}

enum class PasswordResetFailureCategory(val value: String) {
    INVALID_TOKEN("invalid_token"),
    EXPIRED_TOKEN("expired_token"),
    USED_TOKEN("used_token"),
    INVALID_PASSWORD("invalid_password"),
    INVALID_REQUEST("invalid_request"),
    RATE_LIMITED("rate_limited"),
    DISABLED("disabled"),
    INTERNAL("internal"),
}
