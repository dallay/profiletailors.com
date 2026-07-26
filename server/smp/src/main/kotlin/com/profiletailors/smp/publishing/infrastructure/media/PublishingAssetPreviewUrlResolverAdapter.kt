package com.profiletailors.smp.publishing.infrastructure.media

import com.profiletailors.smp.media.application.AssetPreviewUrlResolver
import com.profiletailors.smp.publishing.application.PublishingAssetPreviewUrlResolver
import org.springframework.stereotype.Component

@Component
internal class PublishingAssetPreviewUrlResolverAdapter(private val delegate: AssetPreviewUrlResolver) :
    PublishingAssetPreviewUrlResolver {
    override suspend fun resolvePreviewUrl(
        assetId: String,
        workspaceId: String,
        mediaType: String,
        storageKey: String?,
        externalUrl: String?,
    ): String? = delegate.resolvePreviewUrl(
        assetId = assetId,
        workspaceId = workspaceId,
        mediaType = mediaType,
        storageKey = storageKey,
        externalUrl = externalUrl,
    )
}
