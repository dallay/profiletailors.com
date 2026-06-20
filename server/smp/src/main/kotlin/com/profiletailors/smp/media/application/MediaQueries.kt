package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.media.domain.MediaAssetStatus

/**
 * Query to list workspace media assets with pagination.
 */
data class ListWorkspaceAssetsQuery(
    val workspaceId: String,
    val statuses: Set<MediaAssetStatus> = setOf(MediaAssetStatus.READY),
    val pageSize: Int = 50,
    val cursor: String? = null,
) : Query<ListWorkspaceAssetsResult>

/**
 * Result of listing workspace assets.
 */
data class ListWorkspaceAssetsResult(
    val assets: List<MediaAssetSummary>,
    val nextCursor: String?,
)

/**
 * Query to get a single workspace asset by ID.
 */
data class GetWorkspaceAssetQuery(
    val assetId: String,
    val workspaceId: String,
) : Query<MediaAssetSummary>

/**
 * Query to resolve ready assets for publishing consumption.
 *
 * This is the media-owned port consumed by publishing.
 */
data class ResolveReadyAssetsQuery(
    val workspaceId: String,
    val assetIds: List<String>,
)

/**
 * Result of resolving ready assets.
 */
data class ResolveReadyAssetsResult(
    val assets: List<ResolvedAssetSummary>,
)

/**
 * Summary of a media asset.
 */
data class MediaAssetSummary(
    val assetId: String,
    val workspaceId: String,
    val mediaType: String,
    val sourceType: String,
    val status: String,
    val originalFilename: String?,
    val fileSizeBytes: Long?,
    val createdAt: String,
)

/**
 * Resolved asset summary for publishing consumption.
 */
data class ResolvedAssetSummary(
    val assetId: String,
    val workspaceId: String,
    val storageKey: String,
    val mediaType: String,
)
