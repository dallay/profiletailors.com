package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordResetTokenCleanupPort
import com.profiletailors.smp.identity.infrastructure.observability.PasswordRecoveryObservabilityAdapter
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class PasswordResetTokenCleanupScheduler(
    private val cleanupPort: PasswordResetTokenCleanupPort,
    private val properties: PasswordRecoveryConfigurationProperties,
    private val clock: Clock,
    private val observability: PasswordRecoveryObservabilityAdapter,
) {
    /**
     * Deletes password reset tokens that have expired beyond the configured retention period.
     */
    @Scheduled(
        fixedDelayString = "\${app.identity.password-recovery.cleanup.interval:" +
            "#{T(com.profiletailors.smp.identity.infrastructure.PasswordRecoveryConfigurationProperties.Cleanup)." +
            "DEFAULT_INTERVAL}}",
        initialDelayString = "\${app.identity.password-recovery.cleanup.initial-delay:" +
            "#{T(com.profiletailors.smp.identity.infrastructure.PasswordRecoveryConfigurationProperties.Cleanup)." +
            "DEFAULT_INITIAL_DELAY}}",
    )
    suspend fun runCleanup() {
        runCleanup(clock)
    }

    suspend fun runCleanup(fixedClock: Clock) {
        val cutoff = fixedClock.instant().minus(properties.cleanup.retention)
        val deleted = cleanupPort.deleteExpiredBefore(cutoff)
        observability.recordCleanupDeleted(deleted)
    }
}
