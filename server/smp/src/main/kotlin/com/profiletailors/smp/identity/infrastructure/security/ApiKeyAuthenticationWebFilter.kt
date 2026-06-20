package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.identity.infrastructure.security.IdentitySecurityConfiguration.Companion.WORKSPACE_ACCESS_PATH
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class ApiKeyAuthenticationWebFilter(
    private val converter: ApiKeyPrincipalAuthenticationConverter,
    private val authenticationEntryPoint: ServerAuthenticationEntryPoint,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (path != WORKSPACE_ACCESS_PATH) {
            return chain.filter(exchange)
        }

        return exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?.takeIf { looksLikeApiKey(it) }
            ?.let { bearerValue ->
                converter.convert(bearerValue)
                    .flatMap { authentication ->
                        val filteredExchange = exchange.mutate()
                            .request { builder ->
                                builder.headers { headers ->
                                    headers.remove(HttpHeaders.AUTHORIZATION)
                                }
                            }
                            .build()
                        chain.filter(filteredExchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                    }
                    .onErrorResume(ApiKeyCredentialNotActiveException::class.java) { exception ->
                        authenticationEntryPoint.commence(
                            exchange,
                            BadCredentialsException(exception.message, exception),
                        )
                    }
            }
            ?: chain.filter(exchange)
    }

    private fun looksLikeApiKey(bearerValue: String): Boolean =
        bearerValue.startsWith(API_KEY_PREFIX) && bearerValue.contains(API_KEY_DELIMITER)

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val API_KEY_PREFIX = "ptk_"
        private const val API_KEY_DELIMITER = "."
    }
}
