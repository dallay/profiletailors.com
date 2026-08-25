package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.domain.MediaAsset
import kotlinx.coroutines.flow.Flow

interface MediaAssetPreview {
    suspend fun findAsset(workspaceId: String, assetId: String): MediaAsset?
    fun download(bucket: String, key: String, downloaderId: String): Flow<ByteArray>
}
