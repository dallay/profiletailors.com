package com.profiletailors.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * WebFilter that extracts the workspace ID from the request and propagates it
 * through the reactive context for Row-Level Security (RLS) enforcement.
 *
 * ## Workspace ID Sources
 *
 * 1. **HTTP Header**: `X-Workspace-Id` header (preferred for API clients)
 *
 * If the header is not present, the request proceeds without workspace context.
 * This allows public endpoints and endpoints that get workspaceId from request
 * body to work normally.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // Run early, after security filters
class WorkspaceContextWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val workspaceId = extractWorkspaceId(exchange)

        return if (workspaceId != null) {
            if (log.isDebugEnabled) {
                log.debug("Workspace context set from request: {}", workspaceId)
            }
            chain.filter(exchange)
                .contextWrite(WorkspaceContextHolder.withWorkspace(workspaceId))
        } else {
            chain.filter(exchange)
        }
    }

    private fun extractWorkspaceId(exchange: ServerWebExchange): UUID? {
        val headerValue = exchange.request.headers.getFirst(WORKSPACE_HEADER)
        if (!headerValue.isNullOrBlank()) {
            return parseUuid(headerValue, "header")
        }
        return null
    }

    private fun parseUuid(value: String, source: String): UUID? = try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        val sanitized = value.filter { it.isLetterOrDigit() || it == '-' }.take(MAX_UUID_LENGTH)
        log.warn("Invalid workspace ID format from {}: [sanitized: {}] - {}", source, sanitized, e.message)
        null
    }

    companion object {
        private const val MAX_UUID_LENGTH = 36

        /**
         * HTTP header name for workspace ID.
         */
        const val WORKSPACE_HEADER = "X-Workspace-Id"

        private val log: Logger = LoggerFactory.getLogger(WorkspaceContextWebFilter::class.java)
    }
}
