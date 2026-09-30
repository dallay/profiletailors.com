package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.ValueObject
import java.time.Duration

@ValueObject
data class CredentialProviderRetentionOverride(
    val expiredMetadataRetention: Duration? = null,
    val disconnectGrace: Duration? = null,
    val enabled: Boolean? = null,
) {
    init {
        require(expiredMetadataRetention == null || !expiredMetadataRetention.isNegative) {
            "Provider expired metadata retention must not be negative."
        }
        require(disconnectGrace == null || !disconnectGrace.isNegative) {
            "Provider disconnect grace must not be negative."
        }
    }
}

@ValueObject
data class CredentialRetentionRule(
    val activityId: String,
    val policyVersion: String,
    val expiredMetadataRetention: Duration,
    val disconnectGrace: Duration,
    val enabled: Boolean,
    val providerOverrides: Map<String, CredentialProviderRetentionOverride> = emptyMap(),
) {
    init {
        require(activityId.isNotBlank()) { "Activity id is required." }
        require(!expiredMetadataRetention.isNegative) { "Expired metadata retention must not be negative." }
        require(!disconnectGrace.isNegative) { "Disconnect grace must not be negative." }
    }

    fun resolveFor(provider: SocialProvider): ResolvedCredentialRetention {
        val override = providerOverrides[provider.name.lowercase()] ?: providerOverrides[provider.name]
        return ResolvedCredentialRetention(
            activityId = activityId,
            policyVersion = policyVersion,
            provider = provider,
            expiredMetadataRetention = override?.expiredMetadataRetention ?: expiredMetadataRetention,
            disconnectGrace = override?.disconnectGrace ?: disconnectGrace,
            enabled = override?.enabled ?: enabled,
        )
    }

    companion object {
        const val DEFAULT_ACTIVITY_ID = "pa-006"
        const val DEFAULT_EXPIRED_METADATA_RETENTION_DAYS = 30L
    }
}

@ValueObject
data class ResolvedCredentialRetention(
    val activityId: String,
    val policyVersion: String,
    val provider: SocialProvider,
    val expiredMetadataRetention: Duration,
    val disconnectGrace: Duration,
    val enabled: Boolean,
) {
    init {
        require(activityId.isNotBlank()) { "Activity id is required." }
        require(!expiredMetadataRetention.isNegative) { "Expired metadata retention must not be negative." }
        require(!disconnectGrace.isNegative) { "Disconnect grace must not be negative." }
    }
}
