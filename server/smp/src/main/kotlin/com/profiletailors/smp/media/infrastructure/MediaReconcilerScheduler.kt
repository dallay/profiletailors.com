package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.StaleAssetReconciler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MediaReconcilerScheduler(
    private val staleAssetReconciler: StaleAssetReconciler,
) {
    @Scheduled(fixedRate = FIXED_RATE_MILLIS, initialDelay = INITIAL_DELAY_MILLIS)
    suspend fun runScheduled() {
        staleAssetReconciler.run()
    }

    private companion object {
        private const val FIXED_RATE_MILLIS = 15 * 60 * 1000L
        private const val INITIAL_DELAY_MILLIS = 60 * 1000L
    }
}
