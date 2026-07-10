package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.BlobGarbageCollector
import com.profiletailors.smp.media.application.MediaAssetExpirationJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduler for media background jobs.
 *
 * Runs two jobs:
 * - BlobGarbageCollector: every hour — deletes orphaned storage objects for blobs
 *   past the 7-day retention period.
 * - MediaAssetExpirationJob: every 6 hours — transitions stale PENDING_UPLOAD and
 *   UPLOADING assets to FAILED, and schedules orphaned blobs for GC.
 */
@Component
class MediaReconcilerScheduler(
    private val blobGarbageCollector: BlobGarbageCollector,
    private val mediaAssetExpirationJob: MediaAssetExpirationJob,
) {
    @Scheduled(fixedRate = GC_FIXED_RATE_MILLIS, initialDelay = GC_INITIAL_DELAY_MILLIS)
    suspend fun runGarbageCollector() {
        blobGarbageCollector.run()
    }

    @Scheduled(fixedRate = EXPIRATION_FIXED_RATE_MILLIS, initialDelay = EXPIRATION_INITIAL_DELAY_MILLIS)
    suspend fun runExpirationJob() {
        mediaAssetExpirationJob.run()
    }

    private companion object {
        private const val GC_FIXED_RATE_MILLIS = 60 * 60 * 1000L // 1 hour
        private const val GC_INITIAL_DELAY_MILLIS = 2 * 60 * 1000L // 2 minutes
        private const val EXPIRATION_FIXED_RATE_MILLIS = 6 * 60 * 60 * 1000L // 6 hours
        private const val EXPIRATION_INITIAL_DELAY_MILLIS = 3 * 60 * 1000L // 3 minutes
    }
}
