package com.profiletailors.smp.identity.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthRateLimitWebFilterTest {

    @Test
    fun `allows non-auth endpoints without counting`() {
        val filter = AuthRateLimitWebFilter()
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/media/assets").build())
        var chainInvoked = false

        filter.filter(
            exchange,
            WebFilterChain {
                chainInvoked = true
                Mono.empty()
            },
        ).block()

        assertThat(chainInvoked).isTrue()
    }

    @Test
    fun `returns 429 after exceeding login rate limit`() {
        val filter = AuthRateLimitWebFilter()
        val chain = WebFilterChain { Mono.empty() }

        repeat(20) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                    .remoteAddress(java.net.InetSocketAddress("203.0.113.10", 0))
                    .build(),
            )
            filter.filter(exchange, chain).block()
            assertThat(exchange.response.statusCode).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(java.net.InetSocketAddress("203.0.113.10", 0))
                .build(),
        )
        filter.filter(blocked, chain).block()
        assertThat(blocked.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(blocked.response.headers.getFirst("Retry-After")).isNotBlank()
    }

    @Test
    fun `does not let spoofed forwarded for headers bypass login rate limit`() {
        val filter = AuthRateLimitWebFilter()
        val chain = WebFilterChain { Mono.empty() }

        repeat(20) { index ->
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                    .header("X-Forwarded-For", "198.51.100.$index")
                    .remoteAddress(java.net.InetSocketAddress("203.0.113.10", 0))
                    .build(),
            )
            filter.filter(exchange, chain).block()
            assertThat(exchange.response.statusCode).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/login")
                .header("X-Forwarded-For", "198.51.100.250")
                .remoteAddress(java.net.InetSocketAddress("203.0.113.10", 0))
                .build(),
        )
        filter.filter(blocked, chain).block()

        assertThat(blocked.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }
}
