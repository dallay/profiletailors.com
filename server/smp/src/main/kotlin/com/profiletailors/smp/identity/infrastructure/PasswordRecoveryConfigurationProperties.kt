package com.profiletailors.smp.identity.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private const val DEFAULT_MINIMUM_RESPONSE_DURATION_MILLIS = 250L
private const val DEFAULT_RETENTION_DAYS = 30L
private const val DEFAULT_CLEANUP_INTERVAL_HOURS = 24L
private const val DEFAULT_CLEANUP_INITIAL_DELAY_MINUTES = 5L
private const val DEFAULT_NOTIFICATION_MAX_ATTEMPTS = 3
private const val DEFAULT_NOTIFICATION_INITIAL_BACKOFF_SECONDS = 1L
private const val DEFAULT_NOTIFICATION_MAX_BACKOFF_SECONDS = 30L
private const val DEFAULT_NOTIFICATION_BACKOFF_MULTIPLIER = 2.0

@ConfigurationProperties(prefix = "app.identity.password-recovery")
data class PasswordRecoveryConfigurationProperties(
    val enabled: Boolean = true,
    val minimumResponseDuration: Duration = Duration.ofMillis(DEFAULT_MINIMUM_RESPONSE_DURATION_MILLIS),
    val cleanup: Cleanup = Cleanup(),
    val notificationRetry: NotificationRetry = NotificationRetry(),
) {
    data class Cleanup(
        val retention: Duration = Duration.ofDays(DEFAULT_RETENTION_DAYS),
        val interval: Duration = Duration.ofHours(DEFAULT_CLEANUP_INTERVAL_HOURS),
        val initialDelay: Duration = Duration.ofMinutes(DEFAULT_CLEANUP_INITIAL_DELAY_MINUTES),
    )

    data class NotificationRetry(
        val maxAttempts: Int = DEFAULT_NOTIFICATION_MAX_ATTEMPTS,
        val initialBackoff: Duration = Duration.ofSeconds(DEFAULT_NOTIFICATION_INITIAL_BACKOFF_SECONDS),
        val multiplier: Double = DEFAULT_NOTIFICATION_BACKOFF_MULTIPLIER,
        val maxBackoff: Duration = Duration.ofSeconds(DEFAULT_NOTIFICATION_MAX_BACKOFF_SECONDS),
    ) {
        init {
            require(maxAttempts > 0)
            require(!initialBackoff.isNegative)
            require(multiplier >= 1.0)
            require(!maxBackoff.isNegative)
        }
    }
}
