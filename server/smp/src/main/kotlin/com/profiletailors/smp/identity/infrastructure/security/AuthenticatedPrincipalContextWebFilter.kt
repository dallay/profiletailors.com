package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.platform.infrastructure.RequestContextKeys
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthenticatedPrincipalContextWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
        ReactiveSecurityContextHolder.getContext()
            .mapNotNull { it.authentication }
            .mapNotNull(::extractPrincipal)
            .flatMap { authenticatedPrincipal ->
                chain.filter(exchange)
                    .contextWrite { context ->
                        context.put(RequestContextKeys.principal, authenticatedPrincipal.context)
                    }
                    .thenReturn(Unit)
            }
            .switchIfEmpty(Mono.defer { chain.filter(exchange).thenReturn(Unit) })
            .then()

    private fun extractPrincipal(authentication: Authentication): AuthenticatedPrincipal? =
        when (val principal = authentication.principal) {
            is AuthenticatedPrincipal -> principal
            is ResolvedOidcUser -> principal.authenticatedPrincipal
            else -> null
        }
}
