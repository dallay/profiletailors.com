package com.profiletailors.smp.publishing.application

data class PublishingResolvedAssetSummary(
    val assetId: String,
    val workspaceId: String,
    val storageKey: String,
    val mediaType: String,
)

fun interface PublishingMediaAssetResolver {
    suspend fun resolveReadyAssets(workspaceId: String, assetIds: List<String>): List<PublishingResolvedAssetSummary>
}

fun interface PublishingAssetPreviewUrlResolver {
    suspend fun resolvePreviewUrl(
        assetId: String,
        workspaceId: String,
        mediaType: String,
        storageKey: String?,
        externalUrl: String?,
    ): String?
}

class PublishingAssetNotReadyException(val assetId: String, reason: String) :
    RuntimeException("Asset '$assetId' is not ready: $reason")

class PublishingMediaServiceUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
