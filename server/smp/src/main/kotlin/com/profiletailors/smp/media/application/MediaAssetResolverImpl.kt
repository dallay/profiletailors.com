package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import org.slf4j.LoggerFactory

/**
 * Media-asset resolution port implementation.
 *
 * Resolves ready assets from both the media bounded context and legacy publication_assets rows.
 * This enables publication creation and editing to validate media asset references through a
 * single, unified interface while supporting the transition period.
 *
 * Legacy rows (from `publication_assets` table) are resolved if:
 *  - their `id` matches a requested asset ID
 *  - their `workspace_id` matches the requesting workspace
 *  - their `status` is READY
 *  - their `storage_key` is non-null
 *
 * Media-context-owned rows are resolved through the `mediaAssetRepository`.
 */
@Service
class MediaAssetResolverImpl(
    private val mediaAssetRepository: MediaAssetRepository,
    private val publicationAssetRepository: PublicationAssetRepository,
) : MediaAssetResolver {

    private val logger = LoggerFactory.getLogger(MediaAssetResolverImpl::class.java)

    override suspend fun resolveReadyAssets(
        workspaceId: String,
        assetIds: List<String>,
    ): List<ResolvedAssetSummary> {
        if (assetIds.isEmpty()) return emptyList()

        // 1. Resolve media-context-owned assets
        val mediaAssets = mediaAssetRepository.findByWorkspaceAndIds(workspaceId, assetIds)
        val mediaFoundIds = mediaAssets.map { it.assetId }.toSet()

        // 2. Resolve legacy publication_assets rows for any IDs not found in media context
        val unresolvedIds = assetIds.filter { it !in mediaFoundIds }
        val legacyAssets = if (unresolvedIds.isNotEmpty()) {
            publicationAssetRepository.findByWorkspaceAndIds(workspaceId, unresolvedIds)
                .filter { it.status == com.profiletailors.smp.publishing.domain.PublicationAssetStatus.READY }
                .filter { !it.storageKey.isNullOrBlank() }
        } else {
            emptyList()
        }
        val legacyFoundIds = legacyAssets.map { it.id }.toSet()

        // 3. Detect missing or cross-workspace assets (from both sources)
        for (requestedId in assetIds) {
            val inMedia = requestedId in mediaFoundIds
            val inLegacy = requestedId in legacyFoundIds
            if (!inMedia && !inLegacy) {
                logger.debug(
                    "publishing.asset.validation.rejected publicationId=null assetId={} reason=missing",
                    requestedId,
                )
                throw AssetNotReadyException(requestedId, "asset not found in media context or legacy store")
            }
        }

        // 4. Detect non-READY media assets
        val notReadyMediaAssets = mediaAssets.filter { it.status != MediaAssetStatus.READY }
        for (asset in notReadyMediaAssets) {
            logger.debug(
                "publishing.asset.validation.rejected publicationId=null assetId={} reason=not-ready status={}",
                asset.assetId,
                asset.status,
            )
            throw AssetNotReadyException(asset.assetId, "asset status is ${asset.status}")
        }

        // 5. Build unified result from both sources
        val resolvedMedia = mediaAssets
            .filter { it.status == MediaAssetStatus.READY }
            .map { asset ->
                ResolvedAssetSummary(
                    assetId = asset.assetId,
                    workspaceId = asset.workspaceId,
                    storageKey = asset.storageKey ?: error("READY asset ${asset.assetId} has no storageKey"),
                    mediaType = asset.mediaType,
                )
            }

        val resolvedLegacy = legacyAssets.mapNotNull { asset ->
            val key = asset.storageKey ?: return@mapNotNull null
            ResolvedAssetSummary(
                assetId = asset.id,
                workspaceId = asset.workspaceId,
                storageKey = key,
                mediaType = asset.mediaType,
            )
        }

        return resolvedMedia + resolvedLegacy
    }
}
