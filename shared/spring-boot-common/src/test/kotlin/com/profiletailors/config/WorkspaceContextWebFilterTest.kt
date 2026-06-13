package com.profiletailors.config

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context

class WorkspaceContextWebFilterTest {

    private val filter = WorkspaceContextWebFilter()

    @Test
    fun `should propagate workspace id from request header into reactive context`() {
        val workspaceId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val observedWorkspaceId = AtomicReference<UUID?>()
        val request = MockServerHttpRequest.get("/")
            .header(WorkspaceContextWebFilter.WORKSPACE_HEADER, workspaceId.toString())
            .build()
        val exchange = MockServerWebExchange.from(request)
        val chain = WebFilterChain {
            WorkspaceContextHolder.getWorkspaceId()
                .doOnNext(observedWorkspaceId::set)
                .then()
        }

        filter.filter(exchange, chain).block()

        assertEquals(workspaceId, observedWorkspaceId.get())
    }

    @Test
    fun `should continue without workspace context when header is missing`() {
        val observedWorkspaceId = AtomicReference<UUID?>()
        val request = MockServerHttpRequest.get("/").build()
        val exchange = MockServerWebExchange.from(request)
        val chain = WebFilterChain {
            WorkspaceContextHolder.getWorkspaceId()
                .doOnNext(observedWorkspaceId::set)
                .then()
        }

        filter.filter(exchange, chain).block()

        assertNull(observedWorkspaceId.get())
    }

    @Test
    fun `should ignore invalid workspace header values`() {
        val observedWorkspaceId = AtomicReference<UUID?>()
        val request = MockServerHttpRequest.get("/")
            .header(WorkspaceContextWebFilter.WORKSPACE_HEADER, "not-a-uuid\nforged")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val chain = WebFilterChain {
            WorkspaceContextHolder.getWorkspaceId()
                .doOnNext(observedWorkspaceId::set)
                .then()
        }

        filter.filter(exchange, chain).block()

        assertNull(observedWorkspaceId.get())
    }

    @Test
    fun `holder should read write require and clear workspace context`() {
        val workspaceId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        val fromString = WorkspaceContextHolder.getWorkspaceId()
            .contextWrite(WorkspaceContextHolder.withWorkspace(workspaceId.toString()))
            .block()
        val required = WorkspaceContextHolder.requireWorkspaceId()
            .contextWrite(WorkspaceContextHolder.withWorkspace(workspaceId))
            .block()
        val cleared = WorkspaceContextHolder.getWorkspaceId()
            .contextWrite(WorkspaceContextHolder.clear())
            .contextWrite(WorkspaceContextHolder.withWorkspace(workspaceId))
            .blockOptional()
        val direct = WorkspaceContextHolder.getFromContext(
            WorkspaceContextHolder.withWorkspace(workspaceId)(Context.empty()),
        )

        assertEquals(workspaceId, fromString)
        assertEquals(workspaceId, required)
        assertEquals(workspaceId, direct)
        assertEquals(true, cleared.isEmpty)
    }
}
