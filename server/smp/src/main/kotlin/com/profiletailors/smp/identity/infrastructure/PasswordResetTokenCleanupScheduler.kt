package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordResetTokenCleanupPort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class PasswordResetTokenCleanupScheduler(
    private val cleanupPort: PasswordResetTokenCleanupPort,
    private val properties: PasswordRecoveryConfigurationProperties,
    private val clock: Clock,
) {
    /**
     * Deletes password reset tokens that have expired beyond the configured retention period.
     */
    @Scheduled(
        fixedDelayString = "\${app.identity.password-recovery.cleanup.interval:24h}",
        initialDelayString = "\${app.identity.password-recovery.cleanup.initial-delay:5m}",
    )
    suspend fun runCleanup() {
        val cutoff = clock.instant().minus(properties.cleanup.retention)
        cleanupPort.deleteExpiredBefore(cutoff)
    }
}
