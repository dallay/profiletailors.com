package com.profiletailors.smp.publishing.infrastructure.threads

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties(prefix = "publishing.threads")
data class ThreadsPublishingProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val apiBaseUrl: String = "https://graph.threads.net",
    val authorizationBaseUrl: String = "https://threads.net/oauth/authorize",
    val apiVersion: String = "v1.0",
    val requiredScopes: Set<String> = setOf("threads_basic", "threads_content_publish"),
    val refreshAhead: Duration = Duration.ofHours(1),
    val containerPollInterval: Duration = Duration.ofSeconds(2),
    val containerPollTimeout: Duration = CONTAINER_POLL_TIMEOUT,
    val containerPollMaxAttempts: Int = MAX_CONTAINER_POLL_ATTEMPTS,
    val mediaUrlTtl: Duration = MEDIA_URL_TTL,
) {
    fun isConfigured(): Boolean = enabled &&
        clientId.isNotBlank() &&
        clientSecret.isNotBlank() &&
        redirectUri.isAllowedHttpsRedirect() &&
        apiBaseUrl.isAllowedHttpsBaseUrl() &&
        apiVersion.isNotBlank() &&
        authorizationBaseUrl.isAllowedHttpsBaseUrl() &&
        requiredScopes == APPROVED_SCOPES &&
        refreshAhead.isPositive &&
        containerPollInterval.isPositive &&
        containerPollTimeout.isPositive &&
        containerPollMaxAttempts > 0 &&
        mediaUrlTtl >= containerPollTimeout.multipliedBy(ThreadsCapabilitySet.MAX_CAROUSEL_ITEMS + 1L)
            .plus(MEDIA_URL_SAFETY_MARGIN)

    fun validate() {
        if (enabled && !isConfigured()) {
            throw IllegalStateException("Enabled Threads publishing configuration is invalid.")
        }
    }

    fun isAllowedRedirectUri(candidate: String): Boolean =
        candidate == redirectUri && candidate.isAllowedHttpsRedirect()

    companion object {
        val APPROVED_SCOPES: Set<String> = setOf("threads_basic", "threads_content_publish")
        val CONTAINER_POLL_TIMEOUT: Duration = Duration.ofSeconds(60)
        const val MAX_CONTAINER_POLL_ATTEMPTS: Int = 30
        val MEDIA_URL_SAFETY_MARGIN: Duration = Duration.ofSeconds(5)
        val MEDIA_URL_TTL: Duration = Duration.ofMinutes(25)

        fun disabled(): ThreadsPublishingProperties = ThreadsPublishingProperties()
    }
}

private fun String.isAllowedHttpsRedirect(): Boolean = runCatching {
    URI.create(this).let { uri ->
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }
}.getOrDefault(false)

private fun String.isAllowedHttpsBaseUrl(): Boolean = runCatching {
    URI.create(this).let { uri ->
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }
}.getOrDefault(false)

private val Duration.isPositive: Boolean
    get() = !isZero && !isNegative
