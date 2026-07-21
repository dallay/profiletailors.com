package com.profiletailors.smp.config.adapters

import com.profiletailors.smp.governance.application.AssetStatus
import com.profiletailors.smp.governance.application.AssetStatusUpdate
import com.profiletailors.smp.governance.application.MediaAssetStatusPort
import com.profiletailors.smp.media.application.MediaAssetRepository
import com.profiletailors.smp.media.domain.MediaAssetStatus
import org.springframework.stereotype.Component

/**
 * Adapter that implements the governance port by delegating to the media repository.
 * Lives in config layer to avoid governance depending on media at the module level.
 * Translates between governance types and media types.
 */
@Component
internal class MediaAssetStatusAdapter(private val mediaAssetRepository: MediaAssetRepository) : MediaAssetStatusPort {
    override suspend fun updateAssetStatus(update: AssetStatusUpdate) {
        val mediaStatus = when (update.status) {
            AssetStatus.SUSPENDED -> MediaAssetStatus.SUSPENDED
        }
        mediaAssetRepository.updateStatus(update.assetId, update.workspaceId, mediaStatus)
    }
}
