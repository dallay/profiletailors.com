package com.profiletailors.smp.publishing.domain

import java.time.Instant

interface OAuthStateSigner {
    fun sign(payload: LinkedInOAuthStatePayload): String
    fun verify(state: String): LinkedInOAuthStatePayload
}

data class LinkedInOAuthStatePayload(
    val provider: SocialProvider,
    val workspaceId: String,
    val principalId: String,
    val redirectUri: String,
    val nonce: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

interface LinkedInAuthorizationUrlBuilder {
    fun buildAuthorizationUrl(state: String, redirectUri: String): String
    fun isConfigured(): Boolean
}

class ProviderNotConfiguredException(provider: SocialProvider) :
    IllegalStateException("Provider '$provider' is not configured.")

open class InvalidOAuthStateException(message: String = "OAuth state is invalid.", cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class ExpiredOAuthStateException(message: String = "OAuth state has expired.") : InvalidOAuthStateException(message)

data class ChannelEvent(
    val type: ChannelEventType,
    val workspaceId: String,
    val socialAccountId: String?,
    val occurredAt: Instant,
)

enum class ChannelEventType {
    CONNECTED_CHANNEL_UPDATED,
    CONNECTED_CHANNEL_REMOVED,
}

fun interface ChannelEventPublisher {
    fun publish(event: ChannelEvent)
}
