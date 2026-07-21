package com.profiletailors.smp.governance.application

/**
 * Represents an asset suspension request from governance to media.
 * Uses its own enum to avoid depending on media domain types.
 */
data class AssetStatusUpdate(val workspaceId: String, val assetId: String, val status: AssetStatus)

enum class AssetStatus {
    SUSPENDED,
}
