package com.profiletailors.smp.identity.infrastructure.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain

class SecurityResponseHeadersWebFilterTest {

    private val filter = SecurityResponseHeadersWebFilter()

    @Test
    fun `adds baseline browser hardening headers`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/api/test").build())

        filter.filter(
            exchange,
            WebFilterChain {
                exchange.response.setComplete()
            },
        ).block()

        assertEquals(
            "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; object-src 'none'",
            exchange.response.headers.getFirst("Content-Security-Policy"),
        )
        assertEquals("DENY", exchange.response.headers.getFirst("X-Frame-Options"))
        assertEquals("nosniff", exchange.response.headers.getFirst("X-Content-Type-Options"))
        assertEquals("strict-origin-when-cross-origin", exchange.response.headers.getFirst("Referrer-Policy"))
        assertEquals(
            "camera=(), microphone=(), geolocation=()",
            exchange.response.headers.getFirst("Permissions-Policy"),
        )
        assertNull(exchange.response.headers.getFirst("Strict-Transport-Security"))
    }

    @Test
    fun `adds hsts for secure requests`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("http://internal/api/test")
                .header("X-Forwarded-Proto", "https")
                .build(),
        )

        filter.filter(
            exchange,
            WebFilterChain {
                exchange.response.setComplete()
            },
        ).block()

        assertEquals(
            "max-age=31536000; includeSubDomains; preload",
            exchange.response.headers.getFirst("Strict-Transport-Security"),
        )
    }
}
