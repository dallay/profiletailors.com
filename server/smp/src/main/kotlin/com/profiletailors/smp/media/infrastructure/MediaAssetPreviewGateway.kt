package com.profiletailors.smp.media.infrastructure

import com.profiletailors.smp.media.application.MediaAssetPreview
import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.storage.application.StorageApplicationService
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Repository

@Repository
class MediaAssetPreviewGateway(
    private val mediaAssetRepository: MediaAssetRepository,
    private val storageApplicationService: StorageApplicationService,
) : MediaAssetPreview {

    override suspend fun findAsset(workspaceId: String, assetId: String): MediaAsset? =
        mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)

    override fun download(bucket: String, key: String, downloaderId: String): Flow<ByteArray> =
        storageApplicationService.download(bucket, key, downloaderId)
}
