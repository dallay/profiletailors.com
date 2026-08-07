package com.profiletailors.smp.publishing.infrastructure.socialcontent

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private val DEFAULT_ACTIVITY_CACHE_TTL = Duration.ofHours(48)
private val DEFAULT_COMMENTER_PROFILE_CACHE_TTL = Duration.ofHours(24)
private val DEFAULT_POLL_INTERVAL = Duration.ofMinutes(15)
private val DEFAULT_POLL_OVERLAP = Duration.ofMinutes(10)
private const val MAX_PAGE_SIZE = 100
private val DEFAULT_SUPPORTED_API_VERSIONS = setOf("202606")
private const val MIN_REQUESTS_PER_MINUTE = 1
private const val MIN_BURST = 1
private val DEFAULT_PURGE_INTERVAL = Duration.ofHours(6)

@ConfigurationProperties(prefix = "publishing.social-content")
data class SocialContentProperties(
    val discoveryEnabled: Boolean = false,
    val importEnabled: Boolean = false,
    val inboxEnabled: Boolean = false,
    val repliesEnabled: Boolean = false,
    val syncEnabled: Boolean = false,
    val apiVersion: String = "202606",
    val supportedApiVersions: Set<String> = DEFAULT_SUPPORTED_API_VERSIONS,
    val retentionPolicyVersion: String = "",

    val polling: Polling = Polling(),
    val activityCacheTtl: Duration = DEFAULT_ACTIVITY_CACHE_TTL,
    val commenterProfileCacheTtl: Duration = DEFAULT_COMMENTER_PROFILE_CACHE_TTL,
    val quota: Quota = Quota(),
    val webhooks: Webhooks = Webhooks(),
    val purge: Purge = Purge(),
) {
    data class Polling(
        val interval: Duration = DEFAULT_POLL_INTERVAL,
        val overlap: Duration = DEFAULT_POLL_OVERLAP,
        val pageSize: Int = MAX_PAGE_SIZE,
        val maxPages: Int = 10,
    ) {
        init {
            require(!interval.isNegative && !interval.isZero) { "Polling interval must be positive." }
            require(!overlap.isNegative) { "Polling overlap cannot be negative." }
            require(pageSize in 1..MAX_PAGE_SIZE) {
                "Polling page size must be between 1 and $MAX_PAGE_SIZE."
            }
            require(maxPages >= 1) { "Polling max pages must be at least 1." }
        }
    }

    data class Quota(val requestsPerMinute: Int = 60, val burst: Int = 10) {
        init {
            require(requestsPerMinute >= MIN_REQUESTS_PER_MINUTE) {
                "Quota requests per minute must be at least $MIN_REQUESTS_PER_MINUTE."
            }
            require(burst >= MIN_BURST) { "Quota burst must be at least $MIN_BURST." }
        }
    }

    data class Webhooks(val enabled: Boolean = false, val signingSecret: String = "")

    data class Purge(val enabled: Boolean = false, val interval: Duration = DEFAULT_PURGE_INTERVAL) {
        init {
            require(!interval.isNegative && !interval.isZero) { "Purge interval must be positive." }
        }
    }

    init {
        require(apiVersion.matches(Regex("\\d{4}(0[1-9]|1[0-2])"))) {
            "LinkedIn API version must be YYYYMM with a calendar month."
        }
        require(!activityCacheTtl.isNegative && !activityCacheTtl.isZero) {
            "Activity cache TTL must be positive."
        }
        require(!commenterProfileCacheTtl.isNegative && !commenterProfileCacheTtl.isZero) {
            "Commenter profile cache TTL must be positive."
        }
        require(supportedApiVersions.isNotEmpty() && supportedApiVersions.all(String::isNotBlank)) {
            "Supported LinkedIn API versions must not be empty."
        }
    }
}
