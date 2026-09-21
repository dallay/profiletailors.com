package com.profiletailors.smp.publishing.infrastructure.linkedin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ConfigurableLinkedInAuthorizationUrlBuilderTest {
    @Test
    fun `builds linkedin authorization url with required parameters`() {
        val builder = ConfigurableLinkedInAuthorizationUrlBuilder(properties())

        val url = builder.buildAuthorizationUrl(
            state = "signed.state",
            redirectUri = "https://app.example.com/callback",
        )

        val uri = URI.create(url)
        val query = parseQuery(uri.rawQuery)
        assertEquals("https", uri.scheme)
        assertEquals("www.linkedin.com", uri.host)
        assertEquals("/oauth/v2/authorization", uri.path)
        assertEquals("code", query["response_type"])
        assertEquals("client-id", query["client_id"])
        assertEquals("https://app.example.com/callback", query["redirect_uri"])
        assertEquals("openid profile email w_member_social", query["scope"])
        assertEquals("signed.state", query["state"])
    }

    @Test
    fun `rejects redirect uri outside the registered allowlist`() {
        val builder = ConfigurableLinkedInAuthorizationUrlBuilder(properties())

        assertThrows(IllegalArgumentException::class.java) {
            builder.buildAuthorizationUrl(state = "signed.state", redirectUri = "https://evil.example/cb")
        }
    }

    @Test
    fun `rejects http redirect uri even when registered`() {
        val builder = ConfigurableLinkedInAuthorizationUrlBuilder(
            properties(redirectUri = "http://app.example.com/callback"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            builder.buildAuthorizationUrl(
                state = "signed.state",
                redirectUri = "http://app.example.com/callback",
            )
        }
    }

    @Test
    fun `rejects ip literal redirect uri even when registered`() {
        val builder = ConfigurableLinkedInAuthorizationUrlBuilder(
            properties(redirectUri = "https://192.168.1.10/callback"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            builder.buildAuthorizationUrl(
                state = "signed.state",
                redirectUri = "https://192.168.1.10/callback",
            )
        }
    }

    @Test
    fun `rejects non https scheme redirect uri`() {
        val builder = ConfigurableLinkedInAuthorizationUrlBuilder(properties())

        assertThrows(IllegalArgumentException::class.java) {
            builder.buildAuthorizationUrl(state = "signed.state", redirectUri = "javascript:alert(1)")
        }
    }

    @Test
    fun `is configured requires client id authorization base url and scopes`() {
        assertTrue(ConfigurableLinkedInAuthorizationUrlBuilder(properties()).isConfigured())
        assertFalse(ConfigurableLinkedInAuthorizationUrlBuilder(properties(clientId = "")).isConfigured())
        assertFalse(ConfigurableLinkedInAuthorizationUrlBuilder(properties(authorizationBaseUrl = "")).isConfigured())
        assertFalse(ConfigurableLinkedInAuthorizationUrlBuilder(properties(scopes = "")).isConfigured())
    }

    private fun properties(
        clientId: String = "client-id",
        authorizationBaseUrl: String = "https://www.linkedin.com/oauth/v2/authorization",
        scopes: String = "openid profile email w_member_social",
        redirectUri: String = "https://app.example.com/callback",
    ): LinkedInPublishingProperties = LinkedInPublishingProperties(
        clientId = clientId,
        clientSecret = "client-secret",
        redirectUri = redirectUri,
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
