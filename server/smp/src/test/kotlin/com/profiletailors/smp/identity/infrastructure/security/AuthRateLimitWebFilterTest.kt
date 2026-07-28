package com.profiletailors.smp.identity.infrastructure.security

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

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

        chainInvoked shouldBe true
    }

    @Test
    fun `returns 429 after exceeding login rate limit`() {
        val filter = AuthRateLimitWebFilter()
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.10", 0)

        repeat(20) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                    .remoteAddress(remoteAddress)
                    .build(),
            )
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(remoteAddress)
                .build(),
        )
        filter.filter(blocked, chain).block()
        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        blocked.response.headers.getFirst("Retry-After") shouldNotBe null
        blocked.response.headers.getFirst("Retry-After").orEmpty().isNotBlank() shouldBe true
    }

    @Test
    fun `forgot password uses five request IP bucket and coded problem detail`() {
        val filter = AuthRateLimitWebFilter()
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.11", 0)

        repeat(5) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/forgot-password").remoteAddress(remoteAddress).build(),
            )
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/forgot-password").remoteAddress(remoteAddress).build(),
        )
        filter.filter(blocked, chain).block()

        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        blocked.response.bodyAsString.block()?.contains("AUTH_RATE_LIMIT_EXCEEDED") shouldBe true
    }

    @Test
    fun `reset password uses ten attempt IP bucket`() {
        val filter = AuthRateLimitWebFilter()
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.12", 0)

        repeat(10) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/reset-password").remoteAddress(remoteAddress).build(),
            )
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/reset-password").remoteAddress(remoteAddress).build(),
        )
        filter.filter(blocked, chain).block()

        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
    }

    @Test
    fun `forgot password admits a new request exactly when its window expires`() {
        assertPasswordRecoveryWindowExpires("/api/auth/forgot-password", maxRequests = 5)
    }

    @Test
    fun `reset password admits a new attempt exactly when its window expires`() {
        assertPasswordRecoveryWindowExpires("/api/auth/reset-password", maxRequests = 10)
    }

    @Test
    fun `does not let spoofed forwarded for headers bypass login rate limit`() {
        val filter = AuthRateLimitWebFilter()
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.10", 0)

        repeat(20) { index ->
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                    .header("X-Forwarded-For", "198.51.100.$index")
                    .remoteAddress(remoteAddress)
                    .build(),
            )
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/login")
                .header("X-Forwarded-For", "198.51.100.250")
                .remoteAddress(remoteAddress)
                .build(),
        )
        filter.filter(blocked, chain).block()

        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
    }

    @Test
    fun `evicts stale windows when the bounded map reaches its capacity`() {
        val baseline = Instant.parse("2026-07-01T00:00:00Z")
        val clock = MutableClock(baseline)
        val filter = AuthRateLimitWebFilter(clock, maxTrackedWindows = MAX_TRACKED_WINDOWS_FOR_TEST)
        val chain = WebFilterChain { Mono.empty() }

        repeat(MAX_TRACKED_WINDOWS_FOR_TEST) { index ->
            clock.setInstant(baseline.plusMillis(index.toLong()))
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                    .remoteAddress(InetSocketAddress("198.51.100.$index", 0))
                    .build(),
            )
            filter.filter(exchange, chain).block()
        }
        filter.trackedWindowCount() shouldBe MAX_TRACKED_WINDOWS_FOR_TEST

        clock.setInstant(baseline.plusMillis(WINDOW_MS * 2))
        val newExchanges = (0 until EXTRA_IPS_AFTER_EVICTION).map { offset ->
            MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                    .remoteAddress(InetSocketAddress("192.0.2.$offset", 0))
                    .build(),
            )
        }
        newExchanges.forEach { filter.filter(it, chain).block() }

        filter.trackedWindowCount() shouldBe EXTRA_IPS_AFTER_EVICTION
    }

    private fun assertPasswordRecoveryWindowExpires(path: String, maxRequests: Int) {
        val baseline = Instant.parse("2026-07-01T00:00:00Z")
        val clock = MutableClock(baseline)
        val filter = AuthRateLimitWebFilter(clock)
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.20", 0)
        fun exchange() = MockServerWebExchange.from(
            MockServerHttpRequest.post(path).remoteAddress(remoteAddress).build(),
        )

        repeat(maxRequests) { filter.filter(exchange(), chain).block() }
        val blocked = exchange()
        filter.filter(blocked, chain).block()
        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS

        clock.setInstant(baseline.plusMillis(PASSWORD_RECOVERY_WINDOW_MS))
        val admitted = exchange()
        filter.filter(admitted, chain).block()
        admitted.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
    }

    private companion object {
        private const val WINDOW_MS = 60_000L
        private const val PASSWORD_RECOVERY_WINDOW_MS = 15 * 60_000L
        private const val MAX_TRACKED_WINDOWS_FOR_TEST = 32
        private const val EXTRA_IPS_AFTER_EVICTION = 8
    }

    private class MutableClock(initial: Instant) : Clock() {
        @Volatile private var current: Instant = initial
        fun setInstant(value: Instant) {
            current = value
        }

        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()

        override fun millis(): Long = current.toEpochMilli()

        override fun instant(): Instant = current
    }
}
