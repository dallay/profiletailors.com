package com.profiletailors.smp.governance.application

fun interface MediaAssetStatusReader {
    suspend fun readStatus(workspaceId: String, assetId: String): String?
}
