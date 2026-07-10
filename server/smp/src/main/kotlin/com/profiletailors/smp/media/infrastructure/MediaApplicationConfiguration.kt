package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.AssetPreviewUrlResolver
import com.profiletailors.smp.media.application.MediaPreviewTokenService
import com.profiletailors.smp.media.application.MediaReconcilerSettings
import com.profiletailors.smp.media.application.MediaUploadSettings
import com.profiletailors.smp.media.application.StorageAssetPreviewUrlResolver
import com.profiletailors.storage.domain.AttachmentsStorageBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MediaApplicationConfiguration {
    @Bean
    fun mediaUploadSettings(
        properties: MediaProperties,
        attachmentsStorageBinding: AttachmentsStorageBinding,
    ): MediaUploadSettings = MediaUploadSettings(
        maxConcurrentUploads = properties.maxConcurrentUploads,
        maxCreationsPerHour = properties.maxCreationsPerHour,
        storageBucket = attachmentsStorageBinding.bucketName,
    )

    @Bean
    fun mediaReconcilerSettings(
        properties: MediaProperties,
        attachmentsStorageBinding: AttachmentsStorageBinding,
    ): MediaReconcilerSettings = MediaReconcilerSettings(
        storageBucket = attachmentsStorageBinding.bucketName,
        staleThresholdHours = properties.stale.thresholdHours,
        gracePeriodMinutes = properties.stale.gracePeriodMinutes,
    )

    @Bean
    fun mediaPreviewTokenService(properties: MediaProperties): MediaPreviewTokenService = MediaPreviewTokenService(
        signingSecret = properties.previewSigningSecret,
        previewUrlExpirySeconds = properties.previewUrlExpirySeconds,
    )

    @Bean
    fun assetPreviewUrlResolver(
        attachmentsStorageBinding: AttachmentsStorageBinding,
        mediaPreviewTokenService: MediaPreviewTokenService,
        properties: MediaProperties,
    ): AssetPreviewUrlResolver = StorageAssetPreviewUrlResolver(
        binding = attachmentsStorageBinding,
        mediaPreviewTokenService = mediaPreviewTokenService,
        previewUrlExpirySeconds = properties.previewUrlExpirySeconds,
    )
}
