package com.profiletailors.smp.publishing.infrastructure.media

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import org.slf4j.LoggerFactory

@Service
class MediaAssetResolverImpl(
    private val mediaAssetRepository: MediaAssetRepository,
    private val publicationAssetRepository: PublicationAssetRepository,
) : MediaAssetResolver {

    private val logger = LoggerFactory.getLogger(MediaAssetResolverImpl::class.java)

    override suspend fun resolveReadyAssets(workspaceId: String, assetIds: List<String>): List<ResolvedAssetSummary> {
        if (assetIds.isEmpty()) return emptyList()

        val mediaAssets = mediaAssetRepository.findByWorkspaceAndIds(workspaceId, assetIds)
        val mediaFoundIds = mediaAssets.map { it.assetId }.toSet()

        val unresolvedIds = assetIds.filter { it !in mediaFoundIds }
        val allLegacyAssets = if (unresolvedIds.isNotEmpty()) {
            publicationAssetRepository.findByWorkspaceAndIds(workspaceId, unresolvedIds)
        } else {
            emptyList()
        }
        val legacyPresentIds = allLegacyAssets.map { it.id }.toSet()
        val legacyUsableAssets = allLegacyAssets
            .filter { it.status == PublicationAssetStatus.READY }
            .filter { !it.storageKey.isNullOrBlank() }
        val legacyUsableIds = legacyUsableAssets.map { it.id }.toSet()

        validateAssets(assetIds, mediaFoundIds, mediaAssets, legacyPresentIds, legacyUsableIds, allLegacyAssets)

        val resolvedMedia = mediaAssets
            .filter { it.status == MediaAssetStatus.READY }
            .map { asset ->
                // MediaAsset enforces the invariant at construction: a READY asset always has a
                // non-null storageKey. requireNotNull makes the postcondition explicit at the
                // resolution boundary and keeps the failure type consistent if the model
                // invariants ever change.
                val storageKey = requireNotNull(asset.storageKey) {
                    "READY media asset ${asset.assetId} has no storageKey"
                }
                ResolvedAssetSummary(
                    assetId = asset.assetId,
                    workspaceId = asset.workspaceId,
                    storageKey = storageKey,
                    mediaType = asset.mediaType,
                )
            }

        val resolvedLegacy = legacyUsableAssets.mapNotNull { asset ->
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

    private fun validateAssets(
        assetIds: List<String>,
        mediaFoundIds: Set<String>,
        mediaAssets: List<com.profiletailors.smp.media.domain.MediaAsset>,
        legacyPresentIds: Set<String>,
        legacyUsableIds: Set<String>,
        allLegacyAssets: List<com.profiletailors.smp.publishing.domain.PublicationAsset>,
    ) {
        for (requestedId in assetIds) {
            val inMedia = requestedId in mediaFoundIds
            val inLegacy = requestedId in legacyPresentIds
            val usableInLegacy = requestedId in legacyUsableIds

            if (inMedia) {
                val asset = mediaAssets.first { it.assetId == requestedId }
                if (asset.status != MediaAssetStatus.READY) {
                    logger.debug(
                        "publishing.asset.validation.rejected publicationId=null assetId={} reason=not-ready status={}",
                        asset.assetId,
                        asset.status,
                    )
                    throw AssetNotReadyException(asset.assetId, "asset status is ${asset.status}")
                }
            } else if (inLegacy) {
                if (!usableInLegacy) {
                    val asset = allLegacyAssets.first { it.id == requestedId }
                    logger.debug(
                        "publishing.asset.validation.rejected publicationId=null assetId={} reason=legacy-not-ready",
                        asset.id,
                    )
                    throw AssetNotReadyException(asset.id, "legacy asset status is ${asset.status}")
                }
            } else {
                logger.debug(
                    "publishing.asset.validation.rejected publicationId=null assetId={} reason=missing",
                    requestedId,
                )
                throw AssetNotReadyException(requestedId, "asset not found in media context or legacy store")
            }
        }
    }
}
