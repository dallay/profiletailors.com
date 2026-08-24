package com.profiletailors.smp.config.bridges

import com.profiletailors.smp.governance.application.AssetStatus
import com.profiletailors.smp.governance.application.AssetStatusUpdate
import com.profiletailors.smp.governance.application.MediaAssetStatusUpdater
import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.domain.MediaAssetStatus
import org.springframework.stereotype.Component

@Component
internal class MediaAssetStatusDelegate(private val mediaAssetRepository: MediaAssetRepository) :
    MediaAssetStatusUpdater {
    override suspend fun updateAssetStatus(update: AssetStatusUpdate) {
        val mediaStatus = when (update.status) {
            AssetStatus.SUSPENDED -> MediaAssetStatus.SUSPENDED
        }
        mediaAssetRepository.updateStatus(update.assetId, update.workspaceId, mediaStatus)
    }
}
