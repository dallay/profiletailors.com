package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.profiletailors.smp.publishing.domain.AssetUploadContext
import com.profiletailors.smp.publishing.domain.AssetUploader
import com.profiletailors.smp.publishing.domain.ProviderAssetRef
import com.profiletailors.smp.publishing.domain.PublicationAsset
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Test-only fake for [AssetUploader].
 * Used in unit tests where real LinkedIn asset upload is not needed.
 */
class FakeLinkedInAssetUploader : AssetUploader {
    var failOnNextCall: Boolean = false

    override suspend fun uploadAsset(
        asset: PublicationAsset,
        content: Flow<ByteArray>,
        context: AssetUploadContext,
    ): ProviderAssetRef {
        if (failOnNextCall) {
            failOnNextCall = false
            throw com.profiletailors.smp.publishing.domain.ProviderUploadException(
                "Fake LinkedIn asset upload failure",
            )
        }

        content.collect { /* no-op for fake */ }

        val assetType = when {
            asset.mediaType.startsWith("image/") -> "image"
            asset.mediaType.startsWith("video/") -> "video"
            asset.mediaType.startsWith("document/") -> "document"
            else -> "asset"
        }

        val fakeUrn = "urn:li:digitalmediaAsset:$assetType:fake-asset-${UUID.randomUUID()}"
        return ProviderAssetRef(
            providerAssetId = fakeUrn,
            mediaType = asset.mediaType,
            accessUrl = null,
        )
    }
}
