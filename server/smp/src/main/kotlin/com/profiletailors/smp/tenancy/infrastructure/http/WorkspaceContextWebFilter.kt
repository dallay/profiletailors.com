package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.context.ResourceContext
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
        // filter TWICE for the same request.  On the re-subscription the first
        // invocation's doFinally may have already restored the store to its previous
        // value, so we re-apply the context rather than skipping.
        val previousResourceContext = requestContextStore.currentResourceContext()

        if (exchange.attributes.containsKey(SETUP_DONE_ATTRIBUTE)) {
            // Re-apply the context that was saved on the first invocation — the
            // previous doFinally may have already cleared it.
            val saved = exchange.attributes[SAVED_RESOURCE_CONTEXT_ATTRIBUTE] as ResourceContext?
            requestContextStore.setResourceContext(saved)
            return chain.filter(exchange)
        }
        exchange.attributes[SETUP_DONE_ATTRIBUTE] = true

        val resourceContext = resolver.resolve(headerValue)
        requestContextStore.setResourceContext(resourceContext)
        exchange.attributes[SAVED_RESOURCE_CONTEXT_ATTRIBUTE] = resourceContext

        return chain.filter(exchange)
            .doFinally {
                requestContextStore.setResourceContext(previousResourceContext)
                exchange.attributes.remove(SETUP_DONE_ATTRIBUTE)
            }
    }

    companion object {
        private const val SETUP_DONE_ATTRIBUTE = "WorkspaceContextWebFilter.setupDone"
        private const val SAVED_RESOURCE_CONTEXT_ATTRIBUTE = "WorkspaceContextWebFilter.savedResourceContext"
    }
}
