package com.profiletailors.smp.platform.infrastructure.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class WebFluxConfigurationTest {

    private val configuration = WebFluxConfiguration()
    private val resolver = configuration.mediaTypeVersionResolver()

    @Test
    fun `creates media type version resolver bean`() {
        assertNotNull(resolver)
    }

    @Test
    fun `extracts version from vendor media type accept header`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header("Accept", "application/vnd.api.v2+json")
                .build(),
        )

        assertEquals("2", resolver.resolveVersion(exchange))
    }

    @Test
    fun `returns first matching version when multiple accept headers are present`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header("Accept", "application/json, application/vnd.api.v3+json")
                .build(),
        )

        assertEquals("3", resolver.resolveVersion(exchange))
    }

    @Test
    fun `returns null when accept header does not contain vendor version`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header("Accept", "application/json")
                .build(),
        )

        assertNull(resolver.resolveVersion(exchange))
    }
}
