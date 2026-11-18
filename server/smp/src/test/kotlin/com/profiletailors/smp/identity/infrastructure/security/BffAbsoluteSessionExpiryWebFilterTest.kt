package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.mock.web.server.MockWebSession
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BffAbsoluteSessionExpiryWebFilterTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `invalidates an authenticated session at the absolute expiry`() {
        val session = MockWebSession(clock)
        session.attributes[BffAbsoluteSessionExpiryWebFilter.AUTHENTICATED_AT_ATTRIBUTE] = now.minusSeconds(28_800)
        val exchange = MockServerWebExchange.builder(MockServerHttpRequest.get("/api/private").build())
            .session(session)
            .build()
        var reachedDownstream = false

        BffAbsoluteSessionExpiryWebFilter(clock).filter(
            exchange,
            WebFilterChain {
                reachedDownstream = true
                Mono.empty()
            },
        ).contextWrite(ReactiveSecurityContextHolder.withAuthentication(oidcAuthentication())).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
        assertTrue(session.isExpired)
        assertFalse(reachedDownstream)
    }

    @Test
    fun `keeps an authenticated session before the absolute expiry`() {
        val session = MockWebSession(clock)
        session.attributes[BffAbsoluteSessionExpiryWebFilter.AUTHENTICATED_AT_ATTRIBUTE] = now.minusSeconds(28_799)
        val exchange = MockServerWebExchange.builder(MockServerHttpRequest.get("/api/private").build())
            .session(session)
            .build()
        var reachedDownstream = false

        BffAbsoluteSessionExpiryWebFilter(clock).filter(
            exchange,
            WebFilterChain {
                reachedDownstream = true
                Mono.empty()
            },
        ).contextWrite(ReactiveSecurityContextHolder.withAuthentication(oidcAuthentication())).block()

        assertEquals(null, exchange.response.statusCode)
        assertFalse(session.isExpired)
        assertTrue(reachedDownstream)
    }

    private fun oidcAuthentication(): TestingAuthenticationToken {
        val idToken = OidcIdToken(
            "validated-token",
            now.minusSeconds(1),
            now.plusSeconds(3_600),
            mapOf("iss" to "https://issuer.example", "sub" to "oidc-subject"),
        )
        val principal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "oidc-subject",
                provider = "https://issuer.example",
                authenticationMethod = "OIDC_BFF",
            ),
            credentialType = CredentialType.JWT,
        )
        return TestingAuthenticationToken(
            ResolvedOidcUser(DefaultOidcUser(emptyList(), idToken), principal),
            "",
            emptyList(),
        ).apply { isAuthenticated = true }
    }
}
