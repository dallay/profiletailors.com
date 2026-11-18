package com.profiletailors.smp.identity.infrastructure.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.UriUtils
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

class BffLoginSuccessHandlerTest {
    @Test
    fun `returns to the sanitized path captured at client-specific oidc initiation`() {
        val initiationExchange = MockServerWebExchange.builder(
            MockServerHttpRequest.get("/oauth2/authorization/pt-admin")
                .queryParam("returnTo", "/users/42?tab=roles")
                .build(),
        ).build()
        val captureFilter = BffLoginReturnPathCaptureWebFilter()

        captureFilter.filter(initiationExchange, WebFilterChain { Mono.empty() }).block()
        val session = initiationExchange.session.block()
        assertEquals("/users/42?tab=roles", session?.attributes?.get("bff.oidc.return-path.pt-admin"))

        val callbackExchange = MockServerWebExchange.builder(
            MockServerHttpRequest.get("/login/oauth2/code/pt-admin?code=code&state=state&returnTo=%2Fattacker").build(),
        ).session(requireNotNull(session)).build()
        val authentication = OAuth2AuthenticationToken(
            DefaultOAuth2User(emptyList(), mapOf("sub" to "subject"), "sub"),
            emptyList(),
            "pt-admin",
        )

        BffLoginSuccessHandler(
            "https://app.profiletailors.com/",
            "https://admin.profiletailors.com/",
        ).onAuthenticationSuccess(
            WebFilterExchange(callbackExchange, WebFilterChain { Mono.empty() }),
            authentication,
        ).block()

        assertEquals(
            "https://admin.profiletailors.com/users/42?tab=roles",
            callbackExchange.response.headers.location.toString(),
        )
    }

    @Test
    fun `pt-app login redirects only to the app origin`() {
        assertRedirect(
            clientId = "pt-app",
            returnTo = null,
            expected = "https://app.profiletailors.com/",
        )
    }

    @Test
    fun `pt-admin login redirects only to the admin origin`() {
        assertRedirect(
            clientId = "pt-admin",
            returnTo = null,
            expected = "https://admin.profiletailors.com/",
        )
    }

    @Test
    fun `does not read a return path from the callback query`() {
        assertRedirect(
            clientId = "pt-app",
            returnTo = "/settings/profile",
            expected = "https://app.profiletailors.com/",
        )
    }

    @Test
    fun `rejects an external return destination`() {
        assertRedirect(
            clientId = "pt-app",
            returnTo = "https://evil.example/steal",
            expected = "https://app.profiletailors.com/",
        )
    }

    @Test
    fun `rejects a protocol relative return destination`() {
        assertRedirect(
            clientId = "pt-admin",
            returnTo = "//evil.example/steal",
            expected = "https://admin.profiletailors.com/",
        )
    }

    @Test
    fun `rejects an encoded protocol relative return destination`() {
        assertRedirect(
            clientId = "pt-admin",
            returnTo = "/%2f%2fevil.example/steal",
            expected = "https://admin.profiletailors.com/",
        )
    }

    @Test
    fun `rejects a backslash return path`() {
        assertRedirect(
            clientId = "pt-app",
            returnTo = "/\\evil.example/steal",
            expected = "https://app.profiletailors.com/",
        )
    }

    @Test
    fun `rejects an encoded backslash return path`() {
        assertRedirect(
            clientId = "pt-app",
            returnTo = "/%5c%5cevil.example/steal",
            expected = "https://app.profiletailors.com/",
        )
    }

    private fun assertRedirect(clientId: String, returnTo: String?, expected: String) {
        val callbackPath = "/login/oauth2/code/$clientId"
        val query = returnTo?.let { "?returnTo=${UriUtils.encodeQueryParam(it, StandardCharsets.UTF_8)}" }.orEmpty()
        val request = MockServerHttpRequest.get("$callbackPath$query").build()
        val exchange = MockServerWebExchange.from(request)
        val authentication = OAuth2AuthenticationToken(
            DefaultOAuth2User(emptyList(), mapOf("sub" to "subject"), "sub"),
            emptyList(),
            clientId,
        )

        BffLoginSuccessHandler(
            "https://app.profiletailors.com/",
            "https://admin.profiletailors.com/",
        ).onAuthenticationSuccess(
            WebFilterExchange(exchange, WebFilterChain { Mono.empty() }),
            authentication,
        ).block()

        assertEquals(expected, exchange.response.headers.location.toString())
    }
}
