package com.profiletailors.smp.publishing.infrastructure.linkedin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LinkedInAuthorizationUrlBuilderAdapterTest {
    @Test
    fun `builds linkedin authorization url with required parameters`() {
        val builder = LinkedInAuthorizationUrlBuilderAdapter(properties())

        val url = builder.buildAuthorizationUrl(
            state = "signed.state",
            redirectUri = "https://app.example.com/integrations/linkedin/callback",
        )

        val uri = URI.create(url)
        val query = parseQuery(uri.rawQuery)
        assertEquals("https", uri.scheme)
        assertEquals("www.linkedin.com", uri.host)
        assertEquals("/oauth/v2/authorization", uri.path)
        assertEquals("code", query["response_type"])
        assertEquals("client-id", query["client_id"])
        assertEquals("https://app.example.com/integrations/linkedin/callback", query["redirect_uri"])
        assertEquals("openid profile email w_member_social", query["scope"])
        assertEquals("signed.state", query["state"])
    }

    @Test
    fun `is configured requires client id authorization base url and scopes`() {
        assertTrue(LinkedInAuthorizationUrlBuilderAdapter(properties()).isConfigured())
        assertFalse(LinkedInAuthorizationUrlBuilderAdapter(properties(clientId = "")).isConfigured())
        assertFalse(LinkedInAuthorizationUrlBuilderAdapter(properties(authorizationBaseUrl = "")).isConfigured())
        assertFalse(LinkedInAuthorizationUrlBuilderAdapter(properties(scopes = "")).isConfigured())
    }

    private fun properties(
        clientId: String = "client-id",
        authorizationBaseUrl: String = "https://www.linkedin.com/oauth/v2/authorization",
        scopes: String = "openid profile email w_member_social",
    ): LinkedInPublishingProperties = LinkedInPublishingProperties(
        mode = "fake",
        clientId = clientId,
        clientSecret = "client-secret",
        redirectUri = "https://app.example.com/callback",
        scopes = scopes,
        apiBaseUrl = "https://api.linkedin.com",
        authorizationBaseUrl = authorizationBaseUrl,
        tokenBaseUrl = "https://www.linkedin.com/oauth/v2/accessToken",
        apiVersion = "202601",
    )

    private fun parseQuery(rawQuery: String): Map<String, String> = rawQuery
        .split('&')
        .associate { part ->
            val pieces = part.split('=', limit = 2)
            URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to
                URLDecoder.decode(pieces.getOrElse(1) { "" }, StandardCharsets.UTF_8)
        }
}
