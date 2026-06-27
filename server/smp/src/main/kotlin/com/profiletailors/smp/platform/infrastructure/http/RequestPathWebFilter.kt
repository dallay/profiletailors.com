package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class RequestPathWebFilter(private val requestContextStore: RequestContextStore) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        requestContextStore.setRequestPath(exchange.request.path.pathWithinApplication().value())
        return chain.filter(exchange)
            .doFinally { requestContextStore.setRequestPath(null) }
    }
}
