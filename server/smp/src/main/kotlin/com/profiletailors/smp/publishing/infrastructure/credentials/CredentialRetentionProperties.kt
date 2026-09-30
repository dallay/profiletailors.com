package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.domain.CredentialProviderRetentionOverride
import com.profiletailors.smp.publishing.domain.CredentialRetentionRule
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private val DEFAULT_PURGE_INTERVAL = Duration.ofHours(6)
private val DEFAULT_INITIAL_DELAY = Duration.ofMinutes(5)
private const val DEFAULT_BATCH_SIZE = 100
private const val MAX_BATCH_SIZE = 1000

@ConfigurationProperties(prefix = "publishing.credentials.retention")
data class CredentialRetentionProperties(
    val activityId: String = CredentialRetentionRule.DEFAULT_ACTIVITY_ID,
    val policyVersion: String = "",
    val enabled: Boolean = false,
    val expiredMetadataRetention: Duration = Duration.ofDays(
        CredentialRetentionRule.DEFAULT_EXPIRED_METADATA_RETENTION_DAYS,
    ),
    val disconnectGrace: Duration = Duration.ZERO,
    val interval: Duration = DEFAULT_PURGE_INTERVAL,
    val initialDelay: Duration = DEFAULT_INITIAL_DELAY,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val dryRun: Boolean = false,
    val providers: Map<String, ProviderRetentionOverride> = emptyMap(),
) {
    data class ProviderRetentionOverride(
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

        fun toDomain(): CredentialProviderRetentionOverride = CredentialProviderRetentionOverride(
            expiredMetadataRetention = expiredMetadataRetention,
            disconnectGrace = disconnectGrace,
            enabled = enabled,
        )
    }

    init {
        require(activityId.isNotBlank()) { "Activity id is required." }
        require(!expiredMetadataRetention.isNegative) { "Expired metadata retention must not be negative." }
        require(!disconnectGrace.isNegative) { "Disconnect grace must not be negative." }
        require(!interval.isNegative && !interval.isZero) { "Purge interval must be positive." }
        require(!initialDelay.isNegative) { "Initial delay must not be negative." }
        require(batchSize in 1..MAX_BATCH_SIZE) { "Batch size must be between 1 and $MAX_BATCH_SIZE." }
    }

    fun toRule(): CredentialRetentionRule = CredentialRetentionRule(
        activityId = activityId,
        policyVersion = policyVersion,
        expiredMetadataRetention = expiredMetadataRetention,
        disconnectGrace = disconnectGrace,
        enabled = enabled,
        providerOverrides = providers.mapValues { it.value.toDomain() },
    )
}
