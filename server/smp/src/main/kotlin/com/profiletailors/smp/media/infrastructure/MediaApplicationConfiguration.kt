package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.AssetPreviewUrlResolver
import com.profiletailors.smp.media.application.MediaPreviewTokenService
import com.profiletailors.smp.media.application.MediaReconcilerSettings
import com.profiletailors.smp.media.application.MediaUploadSettings
import com.profiletailors.smp.media.application.StorageAssetPreviewUrlResolver
import com.profiletailors.storage.domain.BucketRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MediaApplicationConfiguration {
    @Bean
    fun mediaUploadSettings(
        properties: MediaProperties,
    ): MediaUploadSettings = MediaUploadSettings(
        maxConcurrentUploads = properties.maxConcurrentUploads,
        maxCreationsPerHour = properties.maxCreationsPerHour,
        storageBucket = properties.storage.bucket,
    )

    @Bean
    fun mediaReconcilerSettings(
        properties: MediaProperties,
    ): MediaReconcilerSettings = MediaReconcilerSettings(
        storageBucket = properties.storage.bucket,
        staleThresholdHours = properties.stale.thresholdHours,
        gracePeriodMinutes = properties.stale.gracePeriodMinutes,
    )

    @Bean
    fun mediaPreviewTokenService(
        properties: MediaProperties,
    ): MediaPreviewTokenService = MediaPreviewTokenService(
        signingSecret = properties.previewSigningSecret,
        previewUrlExpirySeconds = properties.previewUrlExpirySeconds,
    )

    @Bean
    fun assetPreviewUrlResolver(
        bucketRegistry: BucketRegistry,
        mediaPreviewTokenService: MediaPreviewTokenService,
        properties: MediaProperties,
    ): AssetPreviewUrlResolver = StorageAssetPreviewUrlResolver(
        bucketRegistry = bucketRegistry,
        mediaPreviewTokenService = mediaPreviewTokenService,
        storageBucket = properties.storage.bucket,
        previewUrlExpirySeconds = properties.previewUrlExpirySeconds,
    )
}
