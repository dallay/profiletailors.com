package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.infrastructure.InMemoryRequestContextStore
import com.profiletailors.smp.tenancy.application.HeaderActiveWorkspaceContextResolver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class WorkspaceContextWebFilterTest {

    @Test
    fun `stores resolved workspace context for downstream behavior and clears afterwards`() = runTest {
        val store = InMemoryRequestContextStore()
        val filter = WorkspaceContextWebFilter(
            requestContextStore = store,
            resolver = HeaderActiveWorkspaceContextResolver(),
            properties = WorkspaceContextProperties(headerName = "X-Workspace-Id"),
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header("X-Workspace-Id", "workspace-123")
                .build(),
        )
        var resolvedWorkspaceId: String? = null

        filter.filter(
            exchange,
            WebFilterChain {
                resolvedWorkspaceId = store.currentResourceContext()?.workspaceId
                Mono.empty()
            },
        ).block()

        assertEquals("workspace-123", resolvedWorkspaceId)
        assertNull(store.currentResourceContext())
    }

    @Test
    fun `keeps resource context empty when workspace header is absent`() = runTest {
        val store = InMemoryRequestContextStore()
        val filter = WorkspaceContextWebFilter(
            requestContextStore = store,
            resolver = HeaderActiveWorkspaceContextResolver(),
            properties = WorkspaceContextProperties(headerName = "X-Workspace-Id"),
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        var resolvedWorkspaceId: String? = "unexpected"

        filter.filter(
            exchange,
            WebFilterChain {
                resolvedWorkspaceId = store.currentResourceContext()?.workspaceId
                Mono.empty()
            },
        ).block()

        assertNull(resolvedWorkspaceId)
        assertNull(store.currentResourceContext())
    }
}
