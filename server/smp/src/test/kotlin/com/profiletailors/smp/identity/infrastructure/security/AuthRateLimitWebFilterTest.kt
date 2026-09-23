package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.ActiveRefreshSession
import com.profiletailors.smp.credentials.application.CreatedRefreshSession
import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionGateway
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpCookie
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AuthRateLimitWebFilterTest {

    @Test
    fun `allows non-auth endpoints without counting`() {
        val filter = testFilter()
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
        val filter = testFilter()
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
    fun `limits waitlist joins per IP`() {
        val filter = testFilter()
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.44", 0)

        repeat(10) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/waitlists/profile-tailors-launch/entries")
                    .remoteAddress(remoteAddress)
                    .build(),
            )
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/waitlists/profile-tailors-launch/entries")
                .remoteAddress(remoteAddress)
                .build(),
        )
        filter.filter(blocked, chain).block()
        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        blocked.response.headers.getFirst("Retry-After") shouldNotBe null
    }

    @Test
    fun `limits anonymous proxy fetches per IP with a strict budget`() {
        val filter = testFilter()
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.77", 0)

        repeat(10) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/media/proxy?url=https://media.licdn.com/media/test.jpg")
                    .remoteAddress(remoteAddress)
                    .build(),
            )
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/media/proxy?url=https://media.licdn.com/media/test.jpg")
                .remoteAddress(remoteAddress)
                .build(),
        )
        filter.filter(blocked, chain).block()
        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        blocked.response.headers.getFirst("Retry-After") shouldNotBe null
    }

    @Test
    fun `grants a generous budget to proxy requests with a valid session`() {
        val filter = testFilter(FakeRefreshSessionGateway(mapOf("k1.s1" to activeSession("principal-a"))))
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.78", 0)

        repeat(120) {
            val exchange = proxyExchange(remoteAddress, "k1.s1")
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = proxyExchange(remoteAddress, "k1.s1")
        filter.filter(blocked, chain).block()
        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
    }

    @Test
    fun `isolates proxy session budgets by principal`() {
        val filter = testFilter(
            FakeRefreshSessionGateway(
                mapOf(
                    "k1.s1" to activeSession("principal-a"),
                    "k2.s2" to activeSession("principal-b"),
                ),
            ),
        )
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.79", 0)

        repeat(120) {
            val exchange = proxyExchange(remoteAddress, "k1.s1")
            filter.filter(exchange, chain).block()
        }
        val exhausted = proxyExchange(remoteAddress, "k1.s1")
        filter.filter(exhausted, chain).block()
        exhausted.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS

        val other = proxyExchange(remoteAddress, "k2.s2")
        filter.filter(other, chain).block()
        other.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
    }

    @Test
    fun `falls back to the strict bucket when the proxy cookie is forged`() {
        val filter = testFilter(FakeRefreshSessionGateway())
        val chain = WebFilterChain { Mono.empty() }
        val remoteAddress = InetSocketAddress("203.0.113.80", 0)

        repeat(10) {
            val exchange = proxyExchange(remoteAddress, "forged.forged")
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = proxyExchange(remoteAddress, "forged.forged")
        filter.filter(blocked, chain).block()
        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
    }

    private fun proxyExchange(remoteAddress: InetSocketAddress, cookieValue: String?): MockServerWebExchange {
        val request = MockServerHttpRequest.get("/api/media/proxy?url=https://media.licdn.com/media/test.jpg")
            .remoteAddress(remoteAddress)
        if (cookieValue != null) request.cookie(HttpCookie("pt_refresh", cookieValue))
        return MockServerWebExchange.from(request.build())
    }

    private fun activeSession(principalId: String): ActiveRefreshSession = ActiveRefreshSession(
        id = "session-$principalId",
        principalId = principalId,
        lookupKey = "k-$principalId",
        tokenVerifier = "verifier",
        expiresAt = Instant.now().plusSeconds(3600),
        createdAt = Instant.now(),
        lastUsedAt = null,
    )

    private class FakeRefreshSessionGateway(private val sessions: Map<String, ActiveRefreshSession> = emptyMap()) :
        RefreshSessionGateway {
        override suspend fun create(
            principalId: String,
            refreshToken: RefreshSessionToken,
            expiresAt: Instant,
        ): CreatedRefreshSession = throw UnsupportedOperationException()

        override suspend fun requireActive(refreshToken: RefreshSessionToken, now: Instant): ActiveRefreshSession =
            sessions["${refreshToken.lookupKey}.${refreshToken.secret}"]
                ?: throw RefreshSessionNotActiveException(
                    lookupKey = refreshToken.lookupKey,
                    reason = RefreshSessionFailureReason.MISSING,
                )

        override suspend fun rotate(
            currentSessionId: String,
            replacementToken: RefreshSessionToken,
            expiresAt: Instant,
            now: Instant,
        ): CreatedRefreshSession = throw UnsupportedOperationException()

        override suspend fun revoke(currentSessionId: String, now: Instant) = Unit
    }

    private fun testProperties(): RefreshSessionProperties = RefreshSessionProperties(
        cookieName = "pt_refresh",
        cookiePath = "/api",
        sameSite = "Lax",
        secure = true,
        ttlSeconds = 604_800,
    )

    private fun testFilter(
        gateway: RefreshSessionGateway = FakeRefreshSessionGateway(),
        clock: Clock = Clock.systemUTC(),
        maxTrackedWindows: Int = 4_096,
    ): AuthRateLimitWebFilter = AuthRateLimitWebFilter(clock, maxTrackedWindows, gateway, testProperties())

    @Test
    fun `forgot password uses five request IP bucket and coded problem detail`() {
        val filter = testFilter()
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
        val filter = testFilter()
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
        val filter = testFilter()
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
    fun `rejects new identifiers when active windows reach capacity`() {
        val baseline = Instant.parse("2026-07-01T00:00:00Z")
        val clock = MutableClock(baseline)
        val filter = testFilter(clock = clock, maxTrackedWindows = 2)
        val chain = WebFilterChain { Mono.empty() }

        repeat(2) { index ->
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                    .remoteAddress(InetSocketAddress("198.51.100.$index", 0))
                    .build(),
            )
            filter.filter(exchange, chain).block()
            exchange.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        }

        val blocked = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(InetSocketAddress("192.0.2.1", 0))
                .build(),
        )
        filter.filter(blocked, chain).block()

        blocked.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        filter.trackedWindowCount() shouldBe 2

        val existing = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(InetSocketAddress("198.51.100.0", 0))
                .build(),
        )
        filter.filter(existing, chain).block()

        existing.response.statusCode shouldNotBe HttpStatus.TOO_MANY_REQUESTS
        filter.trackedWindowCount() shouldBe 2
    }

    @Test
    fun `serializes admission when concurrent identifiers reach capacity`() {
        val filter = testFilter(maxTrackedWindows = 2)
        val admitted = AtomicInteger()
        val chain = WebFilterChain {
            admitted.incrementAndGet()
            Mono.empty()
        }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)

        try {
            val futures = (0..2).map { index ->
                executor.submit {
                    start.await()
                    val exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.post("/api/auth/login")
                            .remoteAddress(InetSocketAddress("192.0.2.$index", 0))
                            .build(),
                    )
                    filter.filter(exchange, chain).block()
                    exchange.response.statusCode
                }
            }
            start.countDown()

            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            admitted.get() shouldBe 2
            filter.trackedWindowCount() shouldBe 2
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `evicts stale windows when the bounded map reaches its capacity`() {
        val baseline = Instant.parse("2026-07-01T00:00:00Z")
        val clock = MutableClock(baseline)
        val filter = testFilter(clock = clock, maxTrackedWindows = MAX_TRACKED_WINDOWS_FOR_TEST)
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
        val filter = testFilter(clock = clock)
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
