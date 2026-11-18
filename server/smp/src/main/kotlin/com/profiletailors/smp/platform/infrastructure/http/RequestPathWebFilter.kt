package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.smp.platform.infrastructure.RequestContextKeys
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class RequestPathWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        return chain.filter(exchange)
            .contextWrite { context -> context.put(RequestContextKeys.requestPath, path) }
    }
}
