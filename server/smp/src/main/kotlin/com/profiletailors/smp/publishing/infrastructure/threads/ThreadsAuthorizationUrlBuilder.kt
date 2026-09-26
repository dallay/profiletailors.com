package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.OAuthAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.infrastructure.http.formUrlEncoded

class ThreadsAuthorizationUrlBuilder(private val properties: ThreadsPublishingProperties) :
    OAuthAuthorizationUrlBuilder {
    override fun buildAuthorizationUrl(state: String, redirectUri: String): String {
        require(isConfigured()) { "Threads OAuth provider is not configured." }
        require(isAllowedRedirectUri(redirectUri)) { "Threads redirect URI is not allowed." }
        val query = formUrlEncoded(
            "response_type" to "code",
            "client_id" to properties.clientId,
            "redirect_uri" to redirectUri,
            "scope" to properties.requiredScopes.joinToString(","),
            "state" to state,
        )
        return "${properties.authorizationBaseUrl}?$query"
    }

    override fun isConfigured(): Boolean = properties.isConfigured()

    override fun isAllowedRedirectUri(redirectUri: String): Boolean = properties.isAllowedRedirectUri(redirectUri)
}
