package com.profiletailors.smp.media.application

/**
 * Port for resolving ready media assets.
 *
 * This is the primary integration contract consumed by publishing and other contexts.
 * All returned assets are guaranteed to be READY, workspace-scoped, and available for use.
 */
fun interface MediaAssetResolver {
    /**
     * Resolve ready assets by workspace and asset IDs.
     *
     * @param workspaceId The workspace that owns the assets.
     * @param assetIds The list of asset IDs to resolve.
     * @return List of resolved media assets.
     * @throws AssetNotReadyException if any asset is missing, cross-workspace, or not READY.
     * @throws MediaServiceUnavailableException if the media context is unavailable.
     */
    suspend fun resolveReadyAssets(workspaceId: String, assetIds: List<String>): List<ResolvedAssetSummary>
}
