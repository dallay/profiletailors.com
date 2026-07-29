package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailure
import com.profiletailors.smp.identity.application.PasswordResetNotificationFailurePort
import com.profiletailors.smp.identity.application.PasswordResetNotificationStatus
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetry
import com.profiletailors.smp.identity.application.PasswordResetNotificationTelemetryPort
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import com.profiletailors.smp.identity.infrastructure.PasswordRecoveryConfigurationProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import kotlin.math.pow

/**
 * Consumes [PasswordResetRequested] domain events and dispatches password
 * recovery emails.
 *
 * The raw reset token is rendered into the email URL and is never logged.
 * Diagnostic logs include only the principal identifier and stable failure metadata.
 */
@Component
@Subscribe(filterBy = PasswordResetRequested::class)
class SendPasswordResetEmailConsumer(
    private val emailSender: EmailSender,
    private val emailProperties: EmailProperties,
    @Qualifier("passwordResetEmailTaskExecutor") private val taskExecutor: TaskExecutor,
    private val retryPolicy: PasswordRecoveryConfigurationProperties.NotificationRetry,
    private val retryDelay: PasswordResetRetryDelay,
    private val failurePort: PasswordResetNotificationFailurePort,
    private val telemetryPort: PasswordResetNotificationTelemetryPort,
    private val clock: Clock,
) : EventConsumer<PasswordResetRequested> {

    private val log = LoggerFactory.getLogger(SendPasswordResetEmailConsumer::class.java)

    override suspend fun consume(event: PasswordResetRequested) {
        taskExecutor.execute {
            runBlocking { deliver(event) }
        }
    }

    /**
     * Delivers a password reset email, retrying retryable failures until delivery succeeds or attempts are exhausted.
     *
     * @param event The password reset request containing the recipient, token, locale, and principal identifier.
     */
    private suspend fun deliver(event: PasswordResetRequested) {
        val message = EmailTemplates.passwordResetEmail(
            username = event.email.substringBefore('@'),
            token = event.rawResetToken,
            publicAppUrl = emailProperties.publicAppUrl,
            locale = event.locale,
        )
        val subject = if (event.locale.lowercase().startsWith("es")) {
            "Restablece tu contraseña"
        } else {
            "Reset your password"
        }
        var attempt = 1
        while (true) {
            val result = emailSender.send(
                to = event.email,
                subject = subject,
                message = message,
            )
            if (result.success) {
                telemetryPort.record(telemetry(PasswordResetNotificationStatus.SENT, attempt))
                log.info("Password reset email sent for principal '{}'", event.principalId)
                return
            }

            val category = result.failureCategory ?: EmailFailureCategory.PROVIDER_REJECTED
            if (!result.retryable || attempt >= retryPolicy.maxAttempts) {
                recordTerminalFailure(event, attempt, category)
                return
            }

            telemetryPort.record(telemetry(PasswordResetNotificationStatus.RETRYING, attempt, category))
            log.warn(
                "Password reset email delivery retry scheduled for principal '{}' with category '{}' and attempt '{}'",
                event.principalId,
                category.safeName(),
                attempt,
            )
            retryDelay.await(backoffFor(attempt))
            attempt += 1
        }
    }

    /**
     * Records a terminal password reset email delivery failure and its telemetry.
     *
     * @param event The password reset request associated with the failure.
     * @param attempts The number of delivery attempts made.
     * @param category The category of the delivery failure.
     * @throws CancellationException If recording the failure is cancelled.
     */
    private suspend fun recordTerminalFailure(
        event: PasswordResetRequested,
        attempts: Int,
        category: EmailFailureCategory,
    ) {
        var persistenceFailure: RuntimeException? = null
        try {
            failurePort.record(
                PasswordResetNotificationFailure(
                    principalId = event.principalId,
                    notificationType = NOTIFICATION_TYPE,
                    attempts = attempts,
                    failedAt = clock.instant(),
                    category = category,
                ),
            )
        } catch (@Suppress("TooGenericExceptionCaught") failure: RuntimeException) {
            persistenceFailure = failure
        } finally {
            telemetryPort.record(telemetry(PasswordResetNotificationStatus.FAILED, attempts, category))
        }
        if (persistenceFailure is CancellationException) throw persistenceFailure
        if (persistenceFailure != null) {
            log.error("Password reset terminal failure persistence failed for principal '{}'", event.principalId)
        }
        log.error(
            "Password reset email delivery terminal failure for principal '{}' with category '{}' after '{}' attempts",
            event.principalId,
            category.safeName(),
            attempts,
        )
    }

    /**
     * Creates telemetry data for a password reset email notification.
     *
     * @param status The notification status.
     * @param attempts The number of delivery attempts.
     * @param category The email failure category, when applicable.
     * @return The notification telemetry data.
     */
    private fun telemetry(
        status: PasswordResetNotificationStatus,
        attempts: Int,
        category: EmailFailureCategory? = null,
    ): PasswordResetNotificationTelemetry = PasswordResetNotificationTelemetry(
        notificationType = NOTIFICATION_TYPE,
        status = status,
        attempts = attempts,
        category = category,
    )

    /**
     * Calculates the retry delay for a failed delivery attempt.
     *
     * @param failedAttempt The number of the failed attempt.
     * @return The exponential retry delay capped at the configured maximum backoff.
     */
    private fun backoffFor(failedAttempt: Int): Duration {
        val multiplier = retryPolicy.multiplier.pow((failedAttempt - 1).toDouble())
        val millis = (retryPolicy.initialBackoff.toMillis() * multiplier).toLong()
        return Duration.ofMillis(minOf(millis, retryPolicy.maxBackoff.toMillis()))
    }

    /**
 * Formats the failure category name for safe display.
 *
 * @return The lowercase category name with underscores replaced by hyphens.
 */
private fun EmailFailureCategory.safeName(): String = name.lowercase().replace('_', '-')

    private companion object {
        const val NOTIFICATION_TYPE = "PASSWORD_RESET"
    }
}
