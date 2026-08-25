package com.profiletailors.smp.publishing.infrastructure.linkedin

import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder

class ConfigurableLinkedInAuthorizationUrlBuilder(private val properties: LinkedInPublishingProperties) :
    LinkedInAuthorizationUrlBuilder {
    override fun buildAuthorizationUrl(state: String, redirectUri: String): String {
        require(isConfigured()) { "LinkedIn OAuth provider is not configured." }
        val query = formUrlEncoded(
            "response_type" to "code",
            "client_id" to properties.clientId,
            "redirect_uri" to redirectUri,
            "scope" to properties.scopes.trim(),
            "state" to state,
        )
        return "${properties.authorizationBaseUrl}?$query"
    }

    override fun isConfigured(): Boolean = properties.clientId.isNotBlank() &&
        properties.authorizationBaseUrl.isNotBlank() &&
        properties.scopes.isNotBlank()
}
