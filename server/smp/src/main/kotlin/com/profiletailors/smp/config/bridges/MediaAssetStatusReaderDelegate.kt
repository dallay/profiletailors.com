package com.profiletailors.smp.config.bridges

import com.profiletailors.smp.governance.application.MediaAssetStatusReader
import com.profiletailors.smp.media.application.MediaAssetRepository
import org.springframework.stereotype.Component

@Component
internal class MediaAssetStatusReaderDelegate(private val mediaAssetRepository: MediaAssetRepository) :
    MediaAssetStatusReader {
    override suspend fun readStatus(workspaceId: String, assetId: String): String? =
        mediaAssetRepository.findByWorkspaceAndId(workspaceId, assetId)?.status?.name
}
