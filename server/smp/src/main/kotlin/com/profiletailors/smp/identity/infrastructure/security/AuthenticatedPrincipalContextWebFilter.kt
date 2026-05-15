package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.platform.infrastructure.RequestContextStore
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthenticatedPrincipalContextWebFilter(
    private val requestContextStore: RequestContextStore,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
        ReactiveSecurityContextHolder.getContext()
            .mapNotNull { it.authentication }
            .mapNotNull(::extractPrincipal)
            .flatMap { authenticatedPrincipal ->
                requestContextStore.setPrincipalContext(authenticatedPrincipal.context)
                chain.filter(exchange)
                    .doFinally { requestContextStore.clear() }
            }
            .switchIfEmpty(chain.filter(exchange))

    private fun extractPrincipal(authentication: Authentication): AuthenticatedPrincipal? =
        authentication.principal as? AuthenticatedPrincipal
}
