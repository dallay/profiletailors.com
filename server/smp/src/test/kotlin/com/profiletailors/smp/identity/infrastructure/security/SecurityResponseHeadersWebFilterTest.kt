package com.profiletailors.smp.identity.infrastructure.security

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class SecurityResponseHeadersWebFilterTest {

    private val filter = SecurityResponseHeadersWebFilter()

    @Test
    fun `adds baseline browser hardening headers`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/api/test").build())

        filter.filter(exchange, WebFilterChain {
            exchange.response.setComplete()
        }).block()

        exchange.response.headers.getFirst("Content-Security-Policy") shouldBe
            "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; object-src 'none'"
        exchange.response.headers.getFirst("X-Frame-Options") shouldBe "DENY"
        exchange.response.headers.getFirst("X-Content-Type-Options") shouldBe "nosniff"
        exchange.response.headers.getFirst("Referrer-Policy") shouldBe "strict-origin-when-cross-origin"
        exchange.response.headers.getFirst("Permissions-Policy") shouldBe "camera=(), microphone=(), geolocation=()"
        exchange.response.headers.getFirst("Strict-Transport-Security").shouldBeNull()
    }

    @Test
    fun `adds hsts for secure requests`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("http://internal/api/test")
                .header("X-Forwarded-Proto", "https")
                .build(),
        )

        filter.filter(exchange, WebFilterChain {
            exchange.response.setComplete()
        }).block()

        exchange.response.headers.getFirst("Strict-Transport-Security") shouldBe
            "max-age=31536000; includeSubDomains; preload"
    }
}