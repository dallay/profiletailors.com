package com.profiletailors.ratelimit.infrastructure

import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.infrastructure.adapter.ReactiveRateLimitingAdapter
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.filter.RateLimitingFilter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * Unit tests for core [RateLimitingFilter] behavior.
 *
 * Tests cover:
 * - Filter behavior for authentication endpoints
 * - Filter bypass for non-auth endpoints
 * - Rate limit headers addition
 * - Rate limit error response generation
 * - Enable/disable configuration
 * - Request already processed handling
 * - Path matching logic
 *
 * Additional test classes:
 * - [RateLimitingFilterIpExtractionTest] - IP extraction and sanitization
 * - [RateLimitingFilterStrategyTest] - Strategy-specific behavior (RESUME, WAITLIST)
 */

internal class RateLimitingFilterTest {

    private lateinit var filter: RateLimitingFilter
    private lateinit var reactiveRateLimitingAdapter: ReactiveRateLimitingAdapter
    private lateinit var configurationFactory: BucketConfigurationFactory
    private lateinit var jsonMapper: JsonMapper
    private lateinit var chain: WebFilterChain

    @BeforeEach
    fun setUp() {
        reactiveRateLimitingAdapter = mockk()
        configurationFactory = mockk()
        jsonMapper = jsonMapper { addModule(kotlinModule()) }
        chain = mockk()

        filter = RateLimitingFilter(reactiveRateLimitingAdapter, jsonMapper, configurationFactory)

        // Default mocks
        every { configurationFactory.isRateLimitEnabled(RateLimitStrategy.AUTH) } returns true
        every { configurationFactory.isRateLimitEnabled(RateLimitStrategy.BUSINESS) } returns true
        every { configurationFactory.isRateLimitEnabled(RateLimitStrategy.RESUME) } returns true
        every { configurationFactory.isRateLimitEnabled(RateLimitStrategy.WAITLIST) } returns true
        every {
            configurationFactory.getEndpoints(RateLimitStrategy.AUTH)
        } returns listOf("/api/auth/login", "/api/auth/register")
        every {
            configurationFactory.getEndpoints(RateLimitStrategy.BUSINESS)
        } returns emptyList()
        every {
            configurationFactory.getEndpoints(RateLimitStrategy.RESUME)
        } returns listOf("/api/resume/generate")
        every {
            configurationFactory.getEndpoints(RateLimitStrategy.WAITLIST)
        } returns listOf("/api/waitlist")
        every { chain.filter(any()) } returns Mono.empty()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should allow request when rate limit not exceeded`() {
        // Given
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

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { chain.filter(exchange) }
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/auth/login", RateLimitStrategy.AUTH)
        }

        // Verify standard rate limit headers
        exchange.response.headers["X-RateLimit-Limit"]?.get(0) shouldBe "10"
        exchange.response.headers["X-RateLimit-Remaining"]?.get(0) shouldBe "9"
        // X-RateLimit-Reset should be present (Unix timestamp)
        exchange.response.headers["X-RateLimit-Reset"]?.get(0) shouldNotBe null
    }

    @Test
    fun `should deny request when rate limit exceeded`() {
        // Given
        val request = MockServerHttpRequest.post("/api/auth/login")
            .remoteAddress(InetSocketAddress("127.0.0.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val identifier = "IP:127.0.0.1"
        val retryAfter = Duration.ofMinutes(5)

        every {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/auth/login", RateLimitStrategy.AUTH)
        } returns Mono.just(
            RateLimitResult.Denied(
                retryAfter = retryAfter,
                limitCapacity = 10,
                windowDuration = Duration.ofMinutes(1),
            ),
        )

        // When
        val result = filter.filter(exchange, chain)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 0) { chain.filter(exchange) }
        verify(exactly = 1) {
            reactiveRateLimitingAdapter.consumeToken(identifier, "/api/auth/login", RateLimitStrategy.AUTH)
        }

        exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        // Verify standard HTTP Retry-After header
        exchange.response.headers["Retry-After"]?.get(0) shouldBe "300"
        // Verify rate limit headers
        exchange.response.headers["X-RateLimit-Limit"]?.get(0) shouldBe "10"
        // Backward compatibility header (deprecated)
        exchange.response.headers["X-Rate-Limit-Retry-After-Seconds"]?.get(0) shouldBe "300"
    }

    @Test
    fun `should skip rate limiting for non-authentication endpoints`() {
        // Given
        val request = MockServerHttpRequest.get("/api/users/profile").build()
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
    fun `should skip rate limiting when auth rate limiting is disabled`() {
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
}
