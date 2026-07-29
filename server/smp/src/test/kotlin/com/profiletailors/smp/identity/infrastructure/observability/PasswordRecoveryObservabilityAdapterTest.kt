package com.profiletailors.smp.identity.infrastructure.observability

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.PasswordResetNotificationStatus
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasswordRecoveryObservabilityAdapterTest {

    @Test
    @Suppress("LongMethod")
    fun `notification outcomes use bounded pii-free metric labels and span attributes`() {
        val meters = SimpleMeterRegistry()
        val observations = RecordingObservationHandler()
        val adapter = PasswordRecoveryObservabilityAdapter(meters, observationRegistry(observations))

        adapter.record(
            PasswordResetNotificationTelemetry(
                notificationType = "PASSWORD_RESET",
                status = PasswordResetNotificationStatus.SENT,
                attempts = 1,
            ),
        )
        adapter.record(
            PasswordResetNotificationTelemetry(
                notificationType = "PASSWORD_RESET",
                status = PasswordResetNotificationStatus.RETRYING,
                attempts = 2,
                category = EmailFailureCategory.PROVIDER_UNAVAILABLE,
            ),
        )
        adapter.record(
            PasswordResetNotificationTelemetry(
                notificationType = "PASSWORD_RESET",
                status = PasswordResetNotificationStatus.FAILED,
                attempts = 99,
                category = EmailFailureCategory.PROVIDER_REJECTED,
            ),
        )

        assertThat(meters.meters.map { it.id.name }).containsOnly(PASSWORD_RECOVERY_METRIC)
        assertThat(
            meters.meters.map {
                it.id.tags.associate { tag -> tag.key to tag.value }
            },
        ).containsExactlyInAnyOrder(
            mapOf(
                "operation" to "notification_delivery",
                "notification.type" to "password_reset",
                "status" to "success",
                "failure.category" to "none",
                "attempt.bucket" to "first",
            ),
            mapOf(
                "operation" to "notification_delivery",
                "notification.type" to "password_reset",
                "status" to "retry",
                "failure.category" to "provider_unavailable",
                "attempt.bucket" to "retry",
            ),
            mapOf(
                "operation" to "notification_delivery",
                "notification.type" to "password_reset",
                "status" to "terminal_failure",
                "failure.category" to "provider_rejected",
                "attempt.bucket" to "exhausted",
            ),
        )
        assertThat(observations.stopped).hasSize(3)
        assertThat(observations.stopped.map { it.name }).containsOnly(PASSWORD_RECOVERY_OBSERVATION)
        assertThat(observations.stopped.map { it.lowCardinality }).containsExactlyElementsOf(
            meters.meters.map { it.id.tags.associate { tag -> tag.key to tag.value } },
        )
        assertNoSensitiveTelemetry(meters, observations)
    }

    @Test
    fun `reset completed and failed outcomes expose only stable categories`() {
        val meters = SimpleMeterRegistry()
        val observations = RecordingObservationHandler()
        val adapter = PasswordRecoveryObservabilityAdapter(meters, observationRegistry(observations))

        adapter.recordResetCompleted()
        adapter.recordResetFailed(PasswordResetFailureCategory.INVALID_TOKEN)
        adapter.recordResetFailed(PasswordResetFailureCategory.INTERNAL)

        assertThat(
            meters.meters.map {
                it.id.tags.associate { tag -> tag.key to tag.value }
            },
        ).containsExactlyInAnyOrder(
            mapOf(
                "operation" to "reset",
                "notification.type" to "none",
                "status" to "completed",
                "failure.category" to "none",
                "attempt.bucket" to "none",
            ),
            mapOf(
                "operation" to "reset",
                "notification.type" to "none",
                "status" to "failed",
                "failure.category" to "invalid_token",
                "attempt.bucket" to "none",
            ),
            mapOf(
                "operation" to "reset",
                "notification.type" to "none",
                "status" to "failed",
                "failure.category" to "internal",
                "attempt.bucket" to "none",
            ),
        )
        assertThat(observations.stopped).hasSize(3)
        assertNoSensitiveTelemetry(meters, observations)
    }

    private fun observationRegistry(handler: RecordingObservationHandler): ObservationRegistry =
        ObservationRegistry.create().also { it.observationConfig().observationHandler(handler) }

    private fun assertNoSensitiveTelemetry(meters: SimpleMeterRegistry, observations: RecordingObservationHandler) {
        val serialized = buildString {
            append(meters.meters.map { it.id })
            append(observations.stopped)
        }
        SENSITIVE_SENTINELS.forEach { sensitive -> assertThat(serialized).doesNotContain(sensitive) }
    }

    private class RecordingObservationHandler : ObservationHandler<Observation.Context> {
        val stopped = mutableListOf<RecordedObservation>()

        override fun supportsContext(context: Observation.Context): Boolean = true

        override fun onStop(context: Observation.Context) {
            stopped += RecordedObservation(
                name = requireNotNull(context.name),
                lowCardinality = context.lowCardinalityKeyValues.associate { it.key to it.value },
            )
        }
    }

    private data class RecordedObservation(val name: String, val lowCardinality: Map<String, String>)

    private companion object {
        const val PASSWORD_RECOVERY_METRIC = "identity.password.recovery.outcomes"
        const val PASSWORD_RECOVERY_OBSERVATION = "identity.password.recovery"
        val SENSITIVE_SENTINELS = listOf(
            "user@example.com",
            "principal-123",
            "203.0.113.42",
            "raw-token-sensitive",
            "token-hash-sensitive",
            "NewPassword123!",
            "password-hash-sensitive",
            "reset-password?token=",
            "smtp arbitrary provider text",
        )
    }
}
