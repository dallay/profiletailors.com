package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.AssetPreviewUrlResolver
import com.profiletailors.smp.media.application.MediaPreviewTokenService
import com.profiletailors.smp.media.application.MediaReconcilerSettings
import com.profiletailors.smp.media.application.MediaUploadSettings
import com.profiletailors.smp.media.application.StorageAssetPreviewUrlResolver
import com.profiletailors.storage.domain.BucketRegistry
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

    @Bean
    fun mediaPreviewTokenService(
        @Value("\${media.preview-signing-secret:profiletailors-dev-media-preview-secret}") signingSecret: String,
        @Value("\${media.preview-url-expiry-seconds:3600}") previewUrlExpirySeconds: Long,
    ): MediaPreviewTokenService = MediaPreviewTokenService(
        signingSecret = signingSecret,
        previewUrlExpirySeconds = previewUrlExpirySeconds,
    )

    @Bean
    fun assetPreviewUrlResolver(
        bucketRegistry: BucketRegistry,
        mediaPreviewTokenService: MediaPreviewTokenService,
        @Value("\${media.storage.bucket:attachments}") storageBucket: String,
        @Value("\${media.preview-url-expiry-seconds:3600}") previewUrlExpirySeconds: Long,
    ): AssetPreviewUrlResolver = StorageAssetPreviewUrlResolver(
        bucketRegistry = bucketRegistry,
        mediaPreviewTokenService = mediaPreviewTokenService,
        storageBucket = storageBucket,
        previewUrlExpirySeconds = previewUrlExpirySeconds,
    )
}
