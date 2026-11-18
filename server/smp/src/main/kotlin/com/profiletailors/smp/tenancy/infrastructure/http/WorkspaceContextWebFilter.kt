package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.infrastructure.RequestContextKeys
import com.profiletailors.smp.tenancy.application.ActiveWorkspaceContextResolver
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

data class WorkspaceContextProperties(val headerName: String = "X-Workspace-Id")

class WorkspaceContextWebFilter(
    private val resolver: ActiveWorkspaceContextResolver,
    private val properties: WorkspaceContextProperties,
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val headerValue = exchange.request.headers.getFirst(properties.headerName)
        if (headerValue.isNullOrBlank()) {
            return chain.filter(exchange)
        }

        val resourceContext = resolver.resolve(headerValue)
        return chain.filter(exchange)
            .contextWrite { context -> context.put(RequestContextKeys.resource, resourceContext) }
    }
}
