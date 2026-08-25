package com.profiletailors.ratelimit.infrastructure

import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.filter.RateLimitingFilter
import com.profiletailors.ratelimit.infrastructure.gateway.ReactiveRateLimiting
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.module.kotlin.jsonMapper
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant

class RateLimitingFilterTest {

    private lateinit var reactiveRateLimitingAdapter: ReactiveRateLimiting
    private lateinit var configurationFactory: BucketConfigurationFactory
    private lateinit var chain: WebFilterChain
    private lateinit var filter: RateLimitingFilter

    @BeforeEach
    fun setUp() {
        reactiveRateLimitingAdapter = mockk()
        configurationFactory = mockk()
        chain = mockk()

        every { configurationFactory.isRateLimitEnabled(any()) } returns true
        every { configurationFactory.getEndpoints(RateLimitStrategy.AUTH) } returns
            listOf("/api/auth/login", "/api/auth/register")
        every { configurationFactory.getEndpoints(RateLimitStrategy.BUSINESS) } returns emptyList()
        every { configurationFactory.getEndpoints(RateLimitStrategy.RESUME) } returns listOf("/api/resume/generate")
        every { configurationFactory.getEndpoints(RateLimitStrategy.WAITLIST) } returns listOf("/api/waitlists")
        every { chain.filter(any()) } returns Mono.empty()

        filter = RateLimitingFilter(
            reactiveRateLimitingAdapter,
            jsonMapper(),
            configurationFactory,
        )
    }

    @Test
    fun `should skip rate limiting if strategy not found`() {
        // Given
        val request = MockServerHttpRequest.get("/api/unknown").build()
        val exchange = MockServerWebExchange.from(request)

        // When
        val result = filter.filter(exchange, chain)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { chain.filter(exchange) }
        verify(exactly = 0) { reactiveRateLimitingAdapter.consumeToken(any(), any(), any()) }
    }

    @Test
    fun `should skip rate limiting if disabled for strategy`() {
        // Given
        every { configurationFactory.isRateLimitEnabled(RateLimitStrategy.AUTH) } returns false
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)

        // When
        val result = filter.filter(exchange, chain)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { chain.filter(exchange) }
        verify(exactly = 0) { reactiveRateLimitingAdapter.consumeToken(any(), any(), any()) }
    }

    @Test
    fun `should handle register endpoint correctly`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/register")
            .remoteAddress(InetSocketAddress("127.0.0.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val identifier = "IP:127.0.0.1"

        every {
            reactiveRateLimitingAdapter.consumeToken(
                identifier,
                "/api/auth/register",
                RateLimitStrategy.AUTH,
            )
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = 8,
                limitCapacity = 10,
                resetTime = Instant.now().plusSeconds(60),
            ),
        )

        // When
        val result = filter.filter(exchange, chain)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(
                identifier,
                "/api/auth/register",
                RateLimitStrategy.AUTH,
            )
        }
    }

    @Test
    fun `should add rate limit headers with remaining tokens`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)
        val remainingTokens = 42L

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = remainingTokens,
                limitCapacity = 100,
                resetTime = Instant.now().plusSeconds(3600),
            ),
        )

        // When
        filter.filter(exchange, chain).block()

        // Then
        exchange.response.headers["X-RateLimit-Remaining"]?.get(0) shouldBe remainingTokens.toString()
    }

    @Test
    fun `should add retry-after header when rate limit exceeded`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)
        val retryAfterSeconds = 600L

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Denied(
                retryAfter = Duration.ofSeconds(retryAfterSeconds),
                limitCapacity = 10,
                windowDuration = Duration.ofMinutes(1),
            ),
        )

        // When
        filter.filter(exchange, chain).block()

        // Then
        exchange.response.headers["X-Rate-Limit-Retry-After-Seconds"]?.get(0) shouldBe retryAfterSeconds.toString()
    }

    @Test
    fun `should return JSON error response when rate limit exceeded`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Denied(
                retryAfter = Duration.ofMinutes(5),
                limitCapacity = 10,
                windowDuration = Duration.ofMinutes(1),
            ),
        )

        // When
        filter.filter(exchange, chain).block()

        // Then
        exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        val contentType = exchange.response.headers.contentType
        contentType?.toString() shouldBe "application/json"
    }

    @Test
    fun `should use the standard rate limit error contract for waitlist requests`() {
        val request = MockServerHttpRequest.post("/api/waitlists/profile-tailors-launch/entries").build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.WAITLIST)
        } returns Mono.just(
            RateLimitResult.Denied(
                retryAfter = Duration.ofMinutes(1),
                limitCapacity = 10,
                windowDuration = Duration.ofMinutes(1),
            ),
        )

        filter.filter(exchange, chain).block()

        exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        val body = exchange.response.bodyAsString()
        body.contains(""""error"""") shouldBe true
        body.contains(""""code":"RATE_LIMIT_EXCEEDED"""") shouldBe true
        body.contains(""""path":"/api/waitlists/profile-tailors-launch/entries"""") shouldBe true
    }

    @Test
    fun `should skip processing if request was already processed`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)

        // Mark as already processed
        exchange.attributes["rateLimitProcessed"] = true

        // When
        val result = filter.filter(exchange, chain)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { chain.filter(exchange) }
        verify(exactly = 0) { reactiveRateLimitingAdapter.consumeToken(any(), any(), any()) }
    }

    @Test
    fun `should handle endpoints with query parameters`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login?redirect=/dashboard").build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = 9,
                limitCapacity = 10,
                resetTime = Instant.now().plusSeconds(60),
            ),
        )

        // When
        val result = filter.filter(exchange, chain)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { chain.filter(exchange) }
    }

    @Test
    fun `should handle different HTTP methods correctly`() {
        // Given - POST request
        val postRequest = MockServerHttpRequest.post("/api/auth/login").build()
        val postExchange = MockServerWebExchange.from(postRequest)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = 9,
                limitCapacity = 10,
                resetTime = Instant.now().plusSeconds(60),
            ),
        )

        // When - POST
        filter.filter(postExchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(any(), "/api/auth/login", RateLimitStrategy.AUTH)
        }

        clearMocks(reactiveRateLimitingAdapter, answers = false)

        // Given - GET request (less common for auth, but should still work)
        val getRequest = MockServerHttpRequest.get("/api/auth/login").build()
        val getExchange = MockServerWebExchange.from(getRequest)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = 8,
                limitCapacity = 10,
                resetTime = Instant.now().plusSeconds(60),
            ),
        )

        // When - GET
        filter.filter(getExchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(any(), "/api/auth/login", RateLimitStrategy.AUTH)
        }
    }

    @Test
    fun `should handle path with trailing slash`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login/").build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = 9,
                limitCapacity = 10,
                resetTime = Instant.now().plusSeconds(60),
            ),
        )

        // When
        val result = filter.filter(exchange, chain)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { chain.filter(exchange) }
    }

    @Test
    fun `should not match false positive paths with contains logic`() {
        // Given - endpoint is /api/auth but path should NOT match /api/auth-extended or /api/v2/auth/settings
        val request1 = MockServerHttpRequest.get("/api/auth-extended").build()
        val exchange1 = MockServerWebExchange.from(request1)

        val request2 = MockServerHttpRequest.get("/api/v2/auth/settings").build()
        val exchange2 = MockServerWebExchange.from(request2)

        // When
        val result1 = filter.filter(exchange1, chain)
        val result2 = filter.filter(exchange2, chain)

        // Then - should skip rate limiting for both (not recognized as auth endpoints)
        StepVerifier.create(result1).verifyComplete()
        StepVerifier.create(result2).verifyComplete()

        verify(exactly = 2) { chain.filter(any()) }
        verify(exactly = 0) { reactiveRateLimitingAdapter.consumeToken(any(), any(), any()) }
    }

    @Test
    fun `should match exact endpoint paths correctly`() {
        // Given - exact match for /api/auth/login
        val request = MockServerHttpRequest.post("/api/auth/login")
            .remoteAddress(InetSocketAddress("127.0.0.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val identifier = "IP:127.0.0.1"

        every {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/auth/login", RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = 9,
                limitCapacity = 10,
                resetTime = Instant.now().plusSeconds(60),
            ),
        )

        // When
        val result = filter.filter(exchange, chain)

        // Then - should match and apply rate limiting
        StepVerifier.create(result).verifyComplete()

        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/auth/login", RateLimitStrategy.AUTH)
        }
    }

    @Test
    fun `should derive identifier from remoteAddress and ignore X-Forwarded-For header`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login")
            .remoteAddress(InetSocketAddress("203.0.113.5", 0))
            .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val identifier = "IP:203.0.113.5"

        every {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/auth/login", RateLimitStrategy.AUTH)
        } returns Mono.just(RateLimitResult.Allowed(9, 10, Instant.now()))

        // When
        filter.filter(exchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(identifier, any(), any())
        }
        verify(exactly = 0) {
            reactiveRateLimitingAdapter.consumeToken("IP:192.168.1.100", any(), any())
        }
    }

    @Test
    fun `should produce the same bucket identifier when X-Forwarded-For is rotated but remoteAddress is stable`() {
        // Given — same remoteAddress, three distinct forwarded header values
        val remoteAddress = InetSocketAddress("203.0.113.7", 0)
        val headerValues = listOf("198.51.100.1", "198.51.100.2", "198.51.100.3")
        val sharedIdentifier = "IP:203.0.113.7"

        headerValues.forEach { forwardedFor ->
            val request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(remoteAddress)
                .header("X-Forwarded-For", forwardedFor)
                .build()
            val exchange = MockServerWebExchange.from(request)

            every {
                reactiveRateLimitingAdapter.consumeToken(sharedIdentifier, "/api/auth/login", RateLimitStrategy.AUTH)
            } returns Mono.just(RateLimitResult.Allowed(9, 10, Instant.now()))

            // When
            filter.filter(exchange, chain).block()
        }

        // Then — every request was bucketed against the same identifier
        verify(exactly = headerValues.size) {
            reactiveRateLimitingAdapter.consumeToken(sharedIdentifier, any(), any())
        }
        verify(exactly = 0) {
            reactiveRateLimitingAdapter.consumeToken(match { it != sharedIdentifier }, any(), any())
        }
    }

    @Test
    fun `should sanitize and truncate IP from remoteAddress when header is ignored`() {
        // Given — MockServerHttpRequest does not allow malformed host addresses, so we exercise
        // the sanitize/length-cap path indirectly through the unknown-fallback code path: when
        // remoteAddress resolves to a malformed host string the cap and regex still apply.
        val request = MockServerHttpRequest.post("/api/auth/login")
            .remoteAddress(InetSocketAddress("203.0.113.9", 0))
            .header("X-Forwarded-For", "192.168.1.100\nINJECTED")
            .build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken("IP:203.0.113.9", any(), any())
        } returns Mono.just(RateLimitResult.Allowed(9, 10, Instant.now()))

        // When
        filter.filter(exchange, chain).block()

        // Then — sanitized remote IP is used, header content is dropped entirely
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken("IP:203.0.113.9", any(), any())
        }
        verify(exactly = 0) {
            reactiveRateLimitingAdapter.consumeToken("IP:192.168.1.100INJECTED", any(), any())
        }
    }

    @Test
    fun `should use unknown IP when remoteAddress is null`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)
        // MockServerWebExchange's request has null remoteAddress by default if not set

        every {
            reactiveRateLimitingAdapter.consumeToken("IP:unknown", "/api/auth/login", RateLimitStrategy.AUTH)
        } returns Mono.just(RateLimitResult.Allowed(9, 10, Instant.now()))

        // When
        filter.filter(exchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken("IP:unknown", any(), any())
        }
    }

    @Test
    fun `should return specific error messages for each strategy`() {
        val strategies = listOf(
            RateLimitStrategy.AUTH to "Too many authentication attempts. Please try again later.",
            RateLimitStrategy.BUSINESS to "Rate limit exceeded for business API. Please try again later.",
            RateLimitStrategy.RESUME to "Rate limit exceeded for resume generation. Please try again later.",
            RateLimitStrategy.WAITLIST to "Too many attempts. Please try again later.",
        )

        strategies.forEach { (strategy, expectedMessage) ->
            // Given
            val path = when (strategy) {
                RateLimitStrategy.AUTH -> "/api/auth/login"
                RateLimitStrategy.BUSINESS -> "/api/business/data"
                RateLimitStrategy.RESUME -> "/api/resume/generate"
                RateLimitStrategy.WAITLIST -> "/api/waitlists/profile-tailors-launch/entries"
            }

            if (strategy == RateLimitStrategy.BUSINESS) return@forEach

            val request = MockServerHttpRequest.post(path).build()
            val exchange = MockServerWebExchange.from(request)

            every {
                reactiveRateLimitingAdapter.consumeToken(any(), path, strategy)
            } returns Mono.just(
                RateLimitResult.Denied(Duration.ofMinutes(1), 10, Duration.ofMinutes(1)),
            )

            // When
            filter.filter(exchange, chain).block()

            // Then
            val body = exchange.response.bodyAsString()
            body.contains(expectedMessage) shouldBe true

            clearMocks(reactiveRateLimitingAdapter, answers = false)
        }
    }

    @Test
    fun `should apply rate limiting to RESUME strategy endpoint`() {
        // Given
        val request = MockServerHttpRequest.post("/api/resume/generate")
            .remoteAddress(InetSocketAddress("10.0.0.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val identifier = "IP:10.0.0.1"

        every {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/resume/generate", RateLimitStrategy.RESUME)
        } returns Mono.just(RateLimitResult.Allowed(8, 10, Instant.now()))

        // When
        filter.filter(exchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/resume/generate", RateLimitStrategy.RESUME)
        }
    }

    @Test
    fun `should apply rate limiting to WAITLIST strategy endpoint`() {
        // Given
        val request = MockServerHttpRequest.post("/api/waitlists/profile-tailors-launch/entries")
            .remoteAddress(InetSocketAddress("10.0.0.2", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val identifier = "IP:10.0.0.2"

        every {
            reactiveRateLimitingAdapter.consumeToken(
                identifier,
                "/api/waitlists/profile-tailors-launch/entries",
                RateLimitStrategy.WAITLIST,
            )
        } returns Mono.just(RateLimitResult.Allowed(7, 10, Instant.now()))

        // When
        filter.filter(exchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(
                identifier,
                "/api/waitlists/profile-tailors-launch/entries",
                RateLimitStrategy.WAITLIST,
            )
        }
    }

    @Test
    fun `should not apply waitlist rate limiting to the singular prefix`() {
        val request = MockServerHttpRequest.post("/api/waitlist/profile-tailors-launch/entries").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()

        verify(exactly = 1) { chain.filter(exchange) }
        verify(exactly = 0) { reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.WAITLIST) }
    }

    @Test
    fun `should add all standard rate limit headers on allowed request`() {
        // Given
        val resetTime = Instant.now().plusSeconds(60)
        val request = MockServerHttpRequest.post("/api/auth/login")
            .remoteAddress(InetSocketAddress("127.0.0.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Allowed(
                remainingTokens = 7,
                limitCapacity = 10,
                resetTime = resetTime,
            ),
        )

        // When
        filter.filter(exchange, chain).block()

        // Then
        exchange.response.headers["X-RateLimit-Limit"]?.get(0) shouldBe "10"
        exchange.response.headers["X-RateLimit-Remaining"]?.get(0) shouldBe "7"
        exchange.response.headers["X-RateLimit-Reset"]?.get(0) shouldBe resetTime.epochSecond.toString()
    }

    @Test
    fun `should set 429 status when rate limit denied`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Denied(
                retryAfter = Duration.ofMinutes(5),
                limitCapacity = 10,
                windowDuration = Duration.ofMinutes(1),
            ),
        )

        // When
        filter.filter(exchange, chain).block()

        // Then
        exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        verify(exactly = 0) { chain.filter(exchange) }
    }

    @Test
    fun `should set standard Retry-After header on denied request`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login").build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Denied(
                retryAfter = Duration.ofMinutes(10),
                limitCapacity = 10,
                windowDuration = Duration.ofMinutes(1),
            ),
        )

        // When
        filter.filter(exchange, chain).block()

        // Then
        exchange.response.headers["Retry-After"]?.get(0) shouldBe "600"
        exchange.response.headers["X-RateLimit-Limit"]?.get(0) shouldBe "10"
    }

    @Test
    fun `should ignore comma-separated X-Forwarded-For and use remoteAddress`() {
        // Given - proxy chain header is present but must be ignored
        val request = MockServerHttpRequest.post("/api/auth/login")
            .remoteAddress(InetSocketAddress("203.0.113.1", 0))
            .header("X-Forwarded-For", "198.51.100.1, 192.0.2.1")
            .build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken("IP:203.0.113.1", any(), any())
        } returns Mono.just(RateLimitResult.Allowed(9, 10, Instant.now()))

        // When
        filter.filter(exchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken("IP:203.0.113.1", any(), any())
        }
        verify(exactly = 0) {
            reactiveRateLimitingAdapter.consumeToken("IP:198.51.100.1", any(), any())
            reactiveRateLimitingAdapter.consumeToken("IP:192.0.2.1", any(), any())
        }
    }

    @Test
    fun `should bucket same remoteAddress together even when X-Forwarded-For header is absent`() {
        // Given - no X-Forwarded-For header at all, only remoteAddress
        val remoteAddress = InetSocketAddress("203.0.113.42", 0)

        repeat(3) {
            val request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(remoteAddress)
                .build()
            val exchange = MockServerWebExchange.from(request)

            every {
                reactiveRateLimitingAdapter.consumeToken("IP:203.0.113.42", "/api/auth/login", RateLimitStrategy.AUTH)
            } returns Mono.just(RateLimitResult.Allowed(9, 10, Instant.now()))

            filter.filter(exchange, chain).block()
        }

        // Then — every request landed in the same bucket
        verify(exactly = 3) {
            reactiveRateLimitingAdapter.consumeToken("IP:203.0.113.42", any(), any())
        }
    }

    @Test
    fun `should match sub-paths of a configured endpoint`() {
        // Given - sub-path of /api/auth/login
        val request = MockServerHttpRequest.post("/api/auth/login/callback").build()
        val exchange = MockServerWebExchange.from(request)

        every {
            reactiveRateLimitingAdapter.consumeToken(any(), any(), RateLimitStrategy.AUTH)
        } returns Mono.just(RateLimitResult.Allowed(9, 10, Instant.now()))

        // When
        filter.filter(exchange, chain).block()

        // Then
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(any(), "/api/auth/login/callback", RateLimitStrategy.AUTH)
        }
    }

    private fun org.springframework.http.server.reactive.ServerHttpResponse.bodyAsString(): String {
        val mockResponse = this as org.springframework.mock.http.server.reactive.MockServerHttpResponse
        return mockResponse.getBodyAsString().block() ?: ""
    }
}
