package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.MediaReconcilerSettings
import com.profiletailors.smp.media.application.MediaUploadSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MediaApplicationConfiguration {
    @Bean
    fun mediaUploadSettings(
        @Value("\${media.max-concurrent-uploads:5}") maxConcurrentUploads: Int,
        @Value("\${media.max-creations-per-hour:200}") maxCreationsPerHour: Int,
        @Value("\${media.storage.bucket:attachments}") storageBucket: String,
    ): MediaUploadSettings = MediaUploadSettings(
        maxConcurrentUploads = maxConcurrentUploads,
        maxCreationsPerHour = maxCreationsPerHour,
        storageBucket = storageBucket,
    )

    @Bean
    fun mediaReconcilerSettings(
        @Value("\${media.storage.bucket:attachments}") storageBucket: String,
        @Value("\${media.stale.threshold-hours:2}") staleThresholdHours: Long,
        @Value("\${media.stale.grace-period-minutes:30}") gracePeriodMinutes: Long,
    ): MediaReconcilerSettings = MediaReconcilerSettings(
        storageBucket = storageBucket,
        staleThresholdHours = staleThresholdHours,
        gracePeriodMinutes = gracePeriodMinutes,
    )
}
