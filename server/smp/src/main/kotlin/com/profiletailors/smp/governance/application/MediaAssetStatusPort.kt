package com.profiletailors.smp.governance.application

/**
 * Port for governance to request media asset status changes without directly
 * depending on the media module, breaking the governance → media cycle.
 */
fun interface MediaAssetStatusPort {
    suspend fun updateAssetStatus(update: AssetStatusUpdate)
}
