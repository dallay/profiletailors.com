package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.infrastructure.ReactorContextResourceContextProvider
import com.profiletailors.smp.tenancy.application.HeaderActiveWorkspaceContextResolver
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

class WorkspaceContextWebFilterTest {

    @Test
    fun `stores resolved workspace context for downstream behavior and clears afterwards`() = runTest {
        val filter = WorkspaceContextWebFilter(
            resolver = HeaderActiveWorkspaceContextResolver(),
            properties = WorkspaceContextProperties(headerName = "X-Workspace-Id"),
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header("X-Workspace-Id", "00000000-0000-0000-0000-000000000123")
                .build(),
        )
        val provider = ReactorContextResourceContextProvider()
        var resolvedWorkspaceId: String? = null

        filter.filter(
            exchange,
            WebFilterChain {
                mono { provider.current()?.workspaceId }
                    .doOnSuccess { resolvedWorkspaceId = it }
                    .then()
            },
        ).block()

        assertEquals("00000000-0000-0000-0000-000000000123", resolvedWorkspaceId)
    }

    @Test
    fun `keeps resource context empty when workspace header is absent`() = runTest {
        val filter = WorkspaceContextWebFilter(
            resolver = HeaderActiveWorkspaceContextResolver(),
            properties = WorkspaceContextProperties(headerName = "X-Workspace-Id"),
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        val provider = ReactorContextResourceContextProvider()
        var resolvedWorkspaceId: String? = "unexpected"

        filter.filter(
            exchange,
            WebFilterChain {
                mono { provider.current()?.workspaceId }
                    .doOnSuccess { resolvedWorkspaceId = it }
                    .then()
            },
        ).block()

        assertNull(resolvedWorkspaceId)
    }

    @Test
    fun `isolates concurrent workspace contexts in downstream provider`() {
        val filter = WorkspaceContextWebFilter(
            resolver = HeaderActiveWorkspaceContextResolver(),
            properties = WorkspaceContextProperties(headerName = "X-Workspace-Id"),
        )
        val firstWorkspaceId = "00000000-0000-0000-0000-000000000123"
        val secondWorkspaceId = "00000000-0000-0000-0000-000000000456"
        val secondDownstreamEntered = Sinks.one<Unit>()
        val firstDownstreamObserved = Sinks.one<Unit>()
        val provider = ReactorContextResourceContextProvider()
        var firstResolvedWorkspaceId: String? = null
        var secondResolvedWorkspaceId: String? = null
        val firstExchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/").header("X-Workspace-Id", firstWorkspaceId).build(),
        )
        val secondExchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/").header("X-Workspace-Id", secondWorkspaceId).build(),
        )

        val firstRequest = filter.filter(
            firstExchange,
            WebFilterChain {
                secondDownstreamEntered.asMono()
                    .then(
                        mono {
                            firstResolvedWorkspaceId = provider.current()?.workspaceId
                            firstDownstreamObserved.tryEmitValue(Unit)
                            Unit
                        },
                    )
                    .then()
            },
        )
        val secondRequest = filter.filter(
            secondExchange,
            WebFilterChain {
                mono { provider.current()?.workspaceId }
                    .doOnNext { secondResolvedWorkspaceId = it }
                    .doOnSubscribe { secondDownstreamEntered.tryEmitValue(Unit) }
                    .then(firstDownstreamObserved.asMono().then())
            },
        )

        Mono.`when`(firstRequest, secondRequest).block()

        assertAll(
            { assertEquals(firstWorkspaceId, firstResolvedWorkspaceId) },
            { assertEquals(secondWorkspaceId, secondResolvedWorkspaceId) },
        )
    }
}
