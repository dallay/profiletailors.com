package com.profiletailors.smp.privacy.infrastructure.config

import com.profiletailors.smp.privacy.application.FindExpiredRequestsJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduler for privacy background jobs.
 *
 * Runs [FindExpiredRequestsJob] daily to discover data subject requests
 * past their retention expiry.
 */
@Component
class PrivacyScheduler(
    private val findExpiredRequestsJob: FindExpiredRequestsJob,
) {
    @Scheduled(fixedRate = FIXED_RATE_MILLIS, initialDelay = INITIAL_DELAY_MILLIS)
    suspend fun runExpiredRequestsJob() {
        findExpiredRequestsJob.run()
    }

    private companion object {
        /** Run every 24 hours */
        private const val FIXED_RATE_MILLIS = 24 * 60 * 60 * 1000L
        /** Initial delay of 5 minutes to let the app warm up */
        private const val INITIAL_DELAY_MILLIS = 5 * 60 * 1000L
    }
}
