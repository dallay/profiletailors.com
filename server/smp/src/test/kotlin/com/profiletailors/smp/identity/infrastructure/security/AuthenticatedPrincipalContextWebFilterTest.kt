package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.platform.infrastructure.InMemoryRequestContextStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthenticatedPrincipalContextWebFilterTest {

    @Test
    fun `stores repo local principal context for downstream platform access and clears afterwards`() = runTest {
        val store = InMemoryRequestContextStore()
        val filter = AuthenticatedPrincipalContextWebFilter(store)
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

        filter.filter(exchange, WebFilterChain {
            seenDuringChain = store.currentPrincipalContext()
            Mono.empty()
        })
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(SecurityContextImpl(authentication))))
            .block()

        assertEquals(principal.context, seenDuringChain)
        assertNull(store.currentPrincipalContext())
    }
}
