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
data class ListWorkspaceAssetsResult(val assets: List<MediaAssetSummary>, val nextCursor: String?)

/**
 * Query to get a single workspace asset by ID.
 */
data class GetWorkspaceAssetQuery(val assetId: String, val workspaceId: String) : Query<MediaAssetSummary>

/**
 * Query to resolve ready assets for publishing consumption.
 *
 * This is the media-owned port consumed by publishing.
 */
data class ResolveReadyAssetsQuery(val workspaceId: String, val assetIds: List<String>)

/**
 * Result of resolving ready assets.
 */
data class ResolveReadyAssetsResult(val assets: List<ResolvedAssetSummary>)

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
    val fileHash: String?, // CAS: SHA-256 of file content (null for pre-CAS assets)
    val createdAt: String,
    val previewUrl: String? = null,
    val downloadUrl: String? = null,
    val sourceProvider: String? = null,
    val externalId: String? = null,
    val sourceUrl: String? = null,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val metadata: Map<String, Any>? = null,
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

/**
 * Query to stream a media asset content.
 */
data class StreamMediaAssetQuery(
    val assetId: String,
    val workspaceId: String,
    val expiresAt: Long,
    val signature: String,
    val purpose: String,
) : Query<MediaAssetStreamResponse>

/**
 * Result of a media asset stream query.
 */
data class MediaAssetStreamResponse(
    val mediaType: String,
    val originalFilename: String?,
    val storageKey: String,
    val status: MediaAssetStatus,
)
