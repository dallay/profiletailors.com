package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.authentication.CredentialType
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.domain.EmailStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant

class AdminAndFreshMfaAuthorizationWebFilterTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Test
    fun `admin routes reject a validated app session`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/admin/session"),
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(oidcUser("pt-app"), null, emptyList())
        var continued = false
        val chain = WebFilterChain {
            continued = true
            Mono.empty()
        }

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertEquals(403, exchange.response.statusCode?.value())
        assertTrue(!continued)
    }

    @Test
    fun `admin routes defer legacy principal authorization to the existing handler`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/admin/session"),
        )
        val legacyPrincipal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "local-1",
                principalType = PrincipalType.USER,
                subject = "legacy-subject",
                provider = "local",
                displayIdentity = "User",
                authenticationMethod = "LOCAL",
            ),
            credentialType = CredentialType.JWT,
            emailStatus = EmailStatus.VERIFIED,
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(legacyPrincipal, null, emptyList())
        var continued = false
        val chain = WebFilterChain {
            continued = true
            Mono.empty()
        }

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertTrue(continued)
    }

    @Test
    fun `admin route accepts only pt-admin loa2 claims`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/admin/session"),
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(oidcUser("pt-admin"), null, emptyList())
        var continued = false
        val chain = WebFilterChain {
            continued = true
            Mono.empty()
        }

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertTrue(continued)
    }

    @Test
    fun `critical mutation rejects missing freshness or otp evidence`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .post("/api/admin/configuration/registration-mode"),
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            oidcUser("pt-admin", authTime = now.minusSeconds(301), amr = listOf("otp")),
            null,
            emptyList(),
        )

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, responseChain(exchange))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertEquals(403, exchange.response.statusCode?.value())
    }

    @Test
    fun `critical mutation rejects fresh authentication without mfa evidence`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.post("/api/v1/account/close"),
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            oidcUser("pt-app", amr = listOf("pwd")),
            null,
            emptyList(),
        )

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, responseChain(exchange))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertEquals(403, exchange.response.statusCode?.value())
    }

    @Test
    fun `critical mutation preserves authenticated legacy machine principal before cutover`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .post("/api/publishing/publications/publication-1/retry"),
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            "legacy-machine-principal",
            null,
            emptyList(),
        )
        var continued = false
        val chain = WebFilterChain {
            continued = true
            Mono.empty()
        }

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertTrue(continued)
    }

    @Test
    fun `fresh otp evidence allows critical route to continue`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .post("/api/admin/configuration/registration-mode"),
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            oidcUser("pt-admin", authTime = now, amr = listOf("pwd", "otp")),
            null,
            emptyList(),
        )
        var continued = false
        val chain = WebFilterChain {
            continued = true
            Mono.empty()
        }

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertTrue(continued)
    }

    @Test
    fun `identity link route rejects legacy principal`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .post("/api/admin/users/local-1/identity-link"),
        )
        val legacyPrincipal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "local-1",
                principalType = PrincipalType.USER,
                subject = "legacy-subject",
                provider = "local",
                displayIdentity = "User",
                authenticationMethod = "LOCAL",
            ),
            credentialType = CredentialType.JWT,
            emailStatus = EmailStatus.VERIFIED,
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(legacyPrincipal, null, emptyList())

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, responseChain(exchange))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertEquals(403, exchange.response.statusCode?.value())
    }

    @Test
    fun `identity link route requires admin client acr and fresh otp mfa`() {
        val rejectedClaims = listOf(
            Triple("pt-app", "2", listOf("pwd", "otp")),
            Triple("pt-admin", "1", listOf("pwd", "otp")),
            Triple("pt-admin", "2", listOf("pwd", "otp")),
            Triple("pt-admin", "2", listOf("pwd")),
        )

        rejectedClaims.forEach { (client, acr, amr) ->
            val exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                    .post("/api/admin/users/local-1/identity-link"),
            )
            val authentication = UsernamePasswordAuthenticationToken.authenticated(
                oidcUser(
                    client,
                    authTime = if (amr.contains("otp")) now.minusSeconds(301) else now,
                    amr = amr,
                    acr = acr,
                ),
                null,
                emptyList(),
            )

            AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
                .filter(exchange, responseChain(exchange))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block()

            assertEquals(403, exchange.response.statusCode?.value(), "$client / $acr / $amr")
        }
    }

    @Test
    fun `identity link route accepts pt-admin acr2 with fresh otp mfa`() {
        val exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .post("/api/admin/users/local-1/identity-link"),
        )
        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            oidcUser("pt-admin", amr = listOf("pwd", "otp")),
            null,
            emptyList(),
        )
        var continued = false
        val chain = WebFilterChain {
            continued = true
            Mono.empty()
        }

        AdminAndFreshMfaAuthorizationWebFilter(ISSUER) { now }
            .filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block()

        assertTrue(continued)
    }

    @Test
    fun `identity link route requires the configured issuer`() {
        val configuredIssuer = "https://identity.example.test/realms/pt"
        val acceptedExchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .get("/api/admin/session"),
        )
        var accepted = false
        val acceptedChain = WebFilterChain {
            accepted = true
            Mono.empty()
        }
        val validAuthentication = UsernamePasswordAuthenticationToken.authenticated(
            oidcUser("pt-admin", issuer = configuredIssuer),
            null,
            emptyList(),
        )

        AdminAndFreshMfaFilterConfiguration().adminMfaFilter(configuredIssuer)
            .filter(acceptedExchange, acceptedChain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(validAuthentication))
            .block()

        assertTrue(accepted)

        val rejectedExchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .get("/api/admin/session"),
        )
        val mismatchedAuthentication = UsernamePasswordAuthenticationToken.authenticated(
            oidcUser("pt-admin"),
            null,
            emptyList(),
        )

        AdminAndFreshMfaFilterConfiguration().adminMfaFilter(configuredIssuer)
            .filter(rejectedExchange, responseChain(rejectedExchange))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(mismatchedAuthentication))
            .block()

        assertEquals(403, rejectedExchange.response.statusCode?.value())
    }

    private fun responseChain(exchange: org.springframework.web.server.ServerWebExchange) = WebFilterChain {
        exchange.response.setComplete()
    }

    private fun oidcUser(
        client: String,
        authTime: Instant = now,
        amr: List<String> = listOf("otp"),
        acr: String = if (client == "pt-admin") "2" else "1",
        issuer: String = ISSUER,
    ): ResolvedOidcUser {
        val claims = mapOf(
            "iss" to issuer,
            "sub" to "subject-1",
            "aud" to listOf(client),
            "azp" to client,
            "acr" to acr,
            "auth_time" to authTime,
            "amr" to amr,
        )
        val token = OidcIdToken("validated", now.minusSeconds(10), now.plusSeconds(3600), claims)
        val localPrincipal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "local-1",
                principalType = PrincipalType.USER,
                subject = "subject-1",
                provider = issuer,
                displayIdentity = "User",
                authenticationMethod = "OIDC_BFF",
            ),
            credentialType = CredentialType.JWT,
            emailStatus = EmailStatus.VERIFIED,
        )
        return ResolvedOidcUser(DefaultOidcUser(emptyList(), token), localPrincipal)
    }

    private companion object {
        const val ISSUER = "https://keycloak.ahome.quest/realms/pt"
    }
}
