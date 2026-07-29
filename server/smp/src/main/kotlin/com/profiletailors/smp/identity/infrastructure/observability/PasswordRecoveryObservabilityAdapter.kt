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

    fun recordResetCompleted() {
        record(resetOutcome(status = "completed"))
    }

    fun recordResetFailed(category: PasswordResetFailureCategory) {
        record(resetOutcome(status = "failed", failureCategory = category.value))
    }

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

    private fun resetOutcome(status: String, failureCategory: String = "none") = PasswordRecoveryOutcome(
        operation = "reset",
        notificationType = "none",
        status = status,
        failureCategory = failureCategory,
        attemptBucket = "none",
    )

    private fun String.safeNotificationType(): String = when (this) {
        "PASSWORD_RESET" -> "password_reset"
        else -> "unknown"
    }

    private fun PasswordResetNotificationStatus.safeStatus(): String = when (this) {
        PasswordResetNotificationStatus.SENT -> "success"
        PasswordResetNotificationStatus.RETRYING -> "retry"
        PasswordResetNotificationStatus.FAILED -> "terminal_failure"
    }

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
