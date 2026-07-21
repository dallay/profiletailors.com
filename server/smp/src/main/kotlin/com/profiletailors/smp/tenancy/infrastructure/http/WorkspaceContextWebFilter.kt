package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.tenancy.application.ActiveWorkspaceContextResolver
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

data class WorkspaceContextProperties(val headerName: String = "X-Workspace-Id")

class WorkspaceContextWebFilter(
    private val requestContextStore: RequestContextStore,
    private val resolver: ActiveWorkspaceContextResolver,
    private val properties: WorkspaceContextProperties,
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val headerValue = exchange.request.headers.getFirst(properties.headerName)
        if (headerValue.isNullOrBlank()) {
            return chain.filter(exchange)
        }

        // Reactor's contextWrite causes re-subscription of the Mono.defer chain in
        // DefaultWebFilterChain, which re-evaluates invokeFilter() and invokes this
        // filter TWICE for the same request.  We detect the re-subscription via
        // exchange.attributes and skip context setup on the second pass — only the
        // first invocation saves/restores the store.
        if (exchange.attributes.containsKey(SETUP_DONE_ATTRIBUTE)) {
            return chain.filter(exchange)
        }
        exchange.attributes[SETUP_DONE_ATTRIBUTE] = true

        val previousResourceContext = requestContextStore.currentResourceContext()

        val resourceContext = resolver.resolve(headerValue)
        requestContextStore.setResourceContext(resourceContext)

        return chain.filter(exchange)
            .doFinally {
                requestContextStore.setResourceContext(previousResourceContext)
                exchange.attributes.remove(SETUP_DONE_ATTRIBUTE)
            }
    }

    companion object {
        private const val SETUP_DONE_ATTRIBUTE = "WorkspaceContextWebFilter.setupDone"
    }
}
