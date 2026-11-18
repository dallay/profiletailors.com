package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.platform.infrastructure.ReactorContextPrincipalContextProvider
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Instant

class AuthenticatedPrincipalContextWebFilterTest {

    @Test
    fun `isolates concurrent principal contexts in downstream provider`() {
        val filter = AuthenticatedPrincipalContextWebFilter()
        val firstPrincipal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "user-1",
                provider = "https://issuer.example",
            ),
            credentialType = CredentialType.JWT,
        )
        val secondPrincipal = firstPrincipal.copy(
            context = firstPrincipal.context.copy(principalId = "principal-2", subject = "user-2"),
        )
        val firstAuthentication = TestingAuthenticationToken(firstPrincipal, "token-1", emptyList()).apply {
            isAuthenticated = true
        }
        val secondAuthentication = TestingAuthenticationToken(secondPrincipal, "token-2", emptyList()).apply {
            isAuthenticated = true
        }
        val secondDownstreamEntered = Sinks.one<Unit>()
        val firstDownstreamObserved = Sinks.one<Unit>()
        val provider = ReactorContextPrincipalContextProvider()
        var firstResolvedPrincipal: PrincipalContext? = null
        var secondResolvedPrincipal: PrincipalContext? = null

        val firstRequest = filter.filter(
            MockServerWebExchange.from(MockServerHttpRequest.get("/first").build()),
            WebFilterChain {
                secondDownstreamEntered.asMono()
                    .then(
                        mono {
                            firstResolvedPrincipal = provider.current()
                            firstDownstreamObserved.tryEmitValue(Unit)
                            Unit
                        },
                    )
                    .then()
            },
        ).contextWrite(ReactiveSecurityContextHolder.withAuthentication(firstAuthentication))
        val secondRequest = filter.filter(
            MockServerWebExchange.from(MockServerHttpRequest.get("/second").build()),
            WebFilterChain {
                mono { provider.current() }
                    .doOnNext { secondResolvedPrincipal = it }
                    .doOnSubscribe { secondDownstreamEntered.tryEmitValue(Unit) }
                    .then(firstDownstreamObserved.asMono().then())
            },
        ).contextWrite(ReactiveSecurityContextHolder.withAuthentication(secondAuthentication))

        Mono.`when`(firstRequest, secondRequest).block()

        assertAll(
            { assertEquals(firstPrincipal.context, firstResolvedPrincipal) },
            { assertEquals(secondPrincipal.context, secondResolvedPrincipal) },
        )
    }

    @Test
    fun `provides repo local principal context to downstream platform access`() = runTest {
        val filter = AuthenticatedPrincipalContextWebFilter()
        val provider = ReactorContextPrincipalContextProvider()
        val principal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "user-123",
                provider = "https://issuer.example",
            ),
            credentialType = CredentialType.JWT,
        )
        val authentication = TestingAuthenticationToken(principal, "token", emptyList()).apply {
            isAuthenticated = true
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        var seenDuringChain: PrincipalContext? = null
        var chainInvocations = 0

        filter.filter(
            exchange,
            WebFilterChain {
                chainInvocations += 1
                mono { seenDuringChain = provider.current() }.then()
            },
        )
            .contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(SecurityContextImpl(authentication))),
            )
            .block()

        assertEquals(principal.context, seenDuringChain)
        assertEquals(1, chainInvocations)
    }

    @Test
    fun `stores resolved OIDC principal context for authenticated session requests`() = runTest {
        val provider = ReactorContextPrincipalContextProvider()
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
        val idToken = org.springframework.security.oauth2.core.oidc.OidcIdToken(
            "validated-token",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T01:00:00Z"),
            mapOf("iss" to "https://issuer.example", "sub" to "oidc-subject"),
        )
        val oidcUser = ResolvedOidcUser(DefaultOidcUser(emptyList(), idToken), principal)
        val authentication = TestingAuthenticationToken(oidcUser, "", emptyList()).apply {
            isAuthenticated = true
        }
        var seenDuringChain: PrincipalContext? = null

        AuthenticatedPrincipalContextWebFilter().filter(
            MockServerWebExchange.from(MockServerHttpRequest.get("/").build()),
            WebFilterChain {
                mono { seenDuringChain = provider.current() }.then()
            },
        ).contextWrite(
            ReactiveSecurityContextHolder.withSecurityContext(Mono.just(SecurityContextImpl(authentication))),
        ).block()

        assertEquals(principal.context, seenDuringChain)
    }

    @Test
    fun `principal context does not replace resource and request path context`() = runTest {
        val filter = AuthenticatedPrincipalContextWebFilter()
        val principal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "user-123",
                provider = "https://issuer.example",
            ),
            credentialType = CredentialType.JWT,
        )
        val authentication = TestingAuthenticationToken(principal, "token", emptyList()).apply {
            isAuthenticated = true
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())

        var seenPrincipal: PrincipalContext? = null
        var seenWorkspace: String? = null
        var seenPath: String? = null
        val principalProvider = ReactorContextPrincipalContextProvider()
        val resourceProvider = com.profiletailors.smp.platform.infrastructure.ReactorContextResourceContextProvider()
        val pathProvider = com.profiletailors.smp.platform.infrastructure.ReactorContextRequestPathProvider()

        filter.filter(
            exchange,
            WebFilterChain {
                mono {
                    seenPrincipal = principalProvider.current()
                    seenWorkspace = resourceProvider.current()?.workspaceId
                    seenPath = pathProvider.current()
                }.then()
            },
        )
            .contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(SecurityContextImpl(authentication))),
            )
            .contextWrite { context ->
                context.put(
                    com.profiletailors.smp.platform.infrastructure.RequestContextKeys.resource,
                    com.profiletailors.common.domain.context.ResourceContext(
                        type = com.profiletailors.common.domain.context.ResourceContextType.WORKSPACE,
                        workspaceId = "workspace-1",
                    ),
                ).put(
                    com.profiletailors.smp.platform.infrastructure.RequestContextKeys.requestPath,
                    "/api/media/assets",
                )
            }
            .block()

        assertEquals(principal.context, seenPrincipal)
        assertEquals("workspace-1", seenWorkspace)
        assertEquals("/api/media/assets", seenPath)
    }
}
