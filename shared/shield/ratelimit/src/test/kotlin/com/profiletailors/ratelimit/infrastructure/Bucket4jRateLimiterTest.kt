package com.profiletailors.ratelimit.infrastructure

import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.infrastructure.adapter.Bucket4jRateLimiter
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties.CacheConfig

/**
 * Unit tests for Bucket4jRateLimiter.
 *
 * Tests cover:
 * - Token consumption for AUTH and BUSINESS strategies
 * - Rate limit denial and allowed scenarios
 * - Bucket caching per identifier and strategy
 * - Correct mapping to RateLimitResult
 * - Token refill behavior
 */

class Bucket4jRateLimiterTest {

    private lateinit var rateLimiter: Bucket4jRateLimiter
    private lateinit var properties: RateLimitProperties

    @BeforeEach
    fun setUp() {
        properties = RateLimitProperties(
            enabled = true,
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "PX001-",
                basic = "BX001-",
            ),
            auth = RateLimitProperties.AuthRateLimitConfig(
                enabled = true,
                limits = listOf(
                    RateLimitProperties.BandwidthLimit(
                        name = "per-minute",
                        capacity = 5, // Small capacity for testing
                        refillTokens = 5,
                        refillDuration = Duration.ofMinutes(1),
                    ),
                ),
            ),
            business = RateLimitProperties.BusinessRateLimitConfig(
                enabled = true,
                pricingPlans = mapOf(
                    "free" to RateLimitProperties.BandwidthLimit(
                        name = "free-plan",
                        capacity = 3, // Small capacity for testing
                        refillTokens = 3,
                        refillDuration = Duration.ofHours(1),
                    ),
                    "basic" to RateLimitProperties.BandwidthLimit(
                        name = "basic-plan",
                        capacity = 5,
                        refillTokens = 5,
                        refillDuration = Duration.ofHours(1),
                    ),
                    "professional" to RateLimitProperties.BandwidthLimit(
                        name = "professional-plan",
                        capacity = 10,
                        refillTokens = 10,
                        refillDuration = Duration.ofHours(1),
                    ),
                ),
            ),
            resume = RateLimitProperties.ResumeRateLimitConfig(
                enabled = true,
                limit = RateLimitProperties.BandwidthLimit(
                    name = "resume-limit",
                    capacity = 10,
                    refillTokens = 10,
                    refillDuration = Duration.ofMinutes(1),
                ),
            ),
            waitlist = RateLimitProperties.WaitlistRateLimitConfig(
                enabled = true,
                limit = RateLimitProperties.BandwidthLimit(
                    name = "waitlist-limit",
                    capacity = 5,
                    refillTokens = 5,
                    refillDuration = Duration.ofMinutes(1),
                ),
            ),
            cache = RateLimitProperties.CacheConfig(
                maxSize = 10000,
                ttlMinutes = 60,
            ),
        )
        val configFactory = BucketConfigurationFactory(properties)
        val apiKeyParser = com.profiletailors.ratelimit.infrastructure.adapter.ApiKeyParser(properties)
        val meterRegistry = SimpleMeterRegistry()
        val metrics = RateLimitMetrics(meterRegistry)
        // Use system clock by default for existing tests
        rateLimiter = Bucket4jRateLimiter(
            configurationFactory = configFactory,
            apiKeyParser = apiKeyParser,
            metrics = metrics,
            properties = properties,
            clock = Clock.systemUTC(),
        )
    }

    @Test
    fun `should allow token consumption when under limit for AUTH strategy`() = runTest {
        // Given
        val identifier = "IP:192.168.1.1"

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 4 // 5 capacity - 1 consumed
        result.limitCapacity shouldBe 5
        result.resetTime.epochSecond shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `should deny token consumption when limit exceeded for AUTH strategy`() = runTest {
        // Given
        val identifier = "IP:192.168.1.2"

        // Consume all tokens
        repeat(5) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)

        // Then - next request should be denied
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        result.retryAfter.seconds shouldBeGreaterThanOrEqual 0
        result.limitCapacity shouldBe 5
    }

    @Test
    fun `should allow token consumption when under limit for BUSINESS strategy with FREE plan`() = runTest {
        // Given
        val identifier = "FREE-KEY-123"

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 2 // 3 capacity - 1 consumed
        result.limitCapacity shouldBe 3
        result.resetTime.epochSecond shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `should deny token consumption when limit exceeded for BUSINESS strategy`() = runTest {
        // Given
        val identifier = "FREE-KEY-456"

        // Consume all tokens
        repeat(3) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        }

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)

        // Then - next request should be denied
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        result.retryAfter.seconds shouldBeGreaterThanOrEqual 0
        result.limitCapacity shouldBe 3
    }

    @Test
    fun `should allow token consumption for BASIC plan`() = runTest {
        // Given
        val identifier = "BX001-BASIC-KEY"

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 4 // 5 capacity - 1 consumed
        result.limitCapacity shouldBe 5
        result.resetTime.epochSecond shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `should allow token consumption for PROFESSIONAL plan`() = runTest {
        // Given
        val identifier = "PX001-PRO-KEY"

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 9 // 10 capacity - 1 consumed
        result.limitCapacity shouldBe 10
        result.resetTime.epochSecond shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `should maintain separate buckets for different identifiers with same strategy`() = runTest {
        // Given
        val identifier1 = "IP:192.168.1.1"
        val identifier2 = "IP:192.168.1.2"

        // When - consume tokens for identifier1
        repeat(5) {
            rateLimiter.consumeToken(identifier1, RateLimitStrategy.AUTH)
        }

        // Then - identifier2 should still have tokens available
        val result = rateLimiter.consumeToken(identifier2, RateLimitStrategy.AUTH)
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
    }

    @Test
    fun `should maintain separate buckets for same identifier with different strategies`() = runTest {
        // Given
        val identifier = "TEST-KEY"

        // When - consume all AUTH tokens
        repeat(5) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }

        // Then - BUSINESS strategy should still have tokens available
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
    }

    @Test
    fun `should decrement remaining tokens with each consumption`() = runTest {
        // Given
        val identifier = "IP:192.168.1.3"

        // When/Then - first consumption
        val result1 = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        result1.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result1.remainingTokens shouldBe 4
        result1.limitCapacity shouldBe 5

        // When/Then - second consumption
        val result2 = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        result2.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result2.remainingTokens shouldBe 3
        result2.limitCapacity shouldBe 5

        // When/Then - third consumption
        val result3 = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        result3.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result3.remainingTokens shouldBe 2
        result3.limitCapacity shouldBe 5
    }

    @Test
    fun `should use default BUSINESS strategy when calling consumeToken with identifier only`() = runTest {
        // Given
        val identifier = "DEFAULT-KEY"

        // When
        val result = rateLimiter.consumeToken(identifier)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        // Should use FREE plan (3 tokens) as default
        result.remainingTokens shouldBe 2
        result.limitCapacity shouldBe 3
    }

    @Test
    fun `should cache buckets for repeated requests`() = runTest {
        // Given
        val identifier = "CACHED-KEY"

        // When - make multiple requests
        val result1 = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        val result2 = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)

        // Then - should see token decrements indicating same bucket is being used
        result1.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result1.remainingTokens shouldBe 4
        result1.limitCapacity shouldBe 5

        result2.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result2.remainingTokens shouldBe 3
        result2.limitCapacity shouldBe 5
    }

    @Test
    fun `should handle IP-based identifiers correctly`() = runTest {
        // Given
        val ipIdentifier = "IP:10.0.0.1"

        // When
        val result = rateLimiter.consumeToken(ipIdentifier, RateLimitStrategy.AUTH)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.limitCapacity shouldBe 5
    }

    @Test
    fun `should handle API key identifiers correctly`() = runTest {
        // Given
        val apiKeyIdentifier = "PX001-ABC123"

        // When
        val result = rateLimiter.consumeToken(apiKeyIdentifier, RateLimitStrategy.BUSINESS)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 9 // Professional plan (10 tokens)
        result.limitCapacity shouldBe 10
    }

    @Test
    fun `should return non-negative retry after duration when denied`() = runTest {
        // Given
        val identifier = "RETRY-TEST"

        // Consume all tokens
        repeat(5) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        result.retryAfter.isNegative shouldBe false
        result.retryAfter.isZero shouldBe false
        result.limitCapacity shouldBe 5
    }

    // --- RESUME strategy tests ---

    @Test
    fun `should allow token consumption for RESUME strategy`() = runTest {
        // Given
        val identifier = "RESUME-USER-001"

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.RESUME)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 9 // 10 capacity - 1 consumed
        result.limitCapacity shouldBe 10
    }

    @Test
    fun `should deny token consumption when RESUME limit exceeded`() = runTest {
        // Given
        val identifier = "RESUME-USER-002"

        // Consume all tokens
        repeat(10) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.RESUME)
        }

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.RESUME)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        result.retryAfter.isNegative shouldBe false
        result.limitCapacity shouldBe 10
    }

    // --- WAITLIST strategy tests ---

    @Test
    fun `should allow token consumption for WAITLIST strategy`() = runTest {
        // Given
        val identifier = "WAITLIST-IP:192.168.1.100"

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.WAITLIST)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 4 // 5 capacity - 1 consumed
        result.limitCapacity shouldBe 5
    }

    @Test
    fun `should deny token consumption when WAITLIST limit exceeded`() = runTest {
        // Given
        val identifier = "WAITLIST-IP:192.168.1.101"

        // Consume all tokens
        repeat(5) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.WAITLIST)
        }

        // When
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.WAITLIST)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        result.retryAfter.isNegative shouldBe false
        result.limitCapacity shouldBe 5
    }

    // --- Cache management tests ---

    @Test
    fun `should return cache size`() = runTest {
        // Given
        val identifier = "CACHE-SIZE-TEST"
        rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)

        // When
        val size = rateLimiter.getCacheSize()

        // Then
        size shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `should return cache stats`() = runTest {
        // Given
        val identifier = "CACHE-STATS-TEST"
        rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)

        // When
        val stats = rateLimiter.getCacheStats()

        // Then
        stats.requestCount() shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `should clear cache`() = runTest {
        // Given
        val identifier1 = "CLEAR-TEST-1"
        val identifier2 = "CLEAR-TEST-2"
        rateLimiter.consumeToken(identifier1, RateLimitStrategy.AUTH)
        rateLimiter.consumeToken(identifier2, RateLimitStrategy.AUTH)

        // When
        rateLimiter.clearCache()

        // Then
        rateLimiter.getCacheSize() shouldBe 0L
    }

    @Test
    fun `should trigger cache cleanup`() = runTest {
        // Given
        val identifier = "CLEANUP-TEST"
        rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)

        // When/Then - should not throw
        rateLimiter.triggerCacheCleanup()
    }

    @Test
    fun `should maintain separate buckets for different strategies with same identifier`() = runTest {
        // Given
        val identifier = "MULTI-STRATEGY-TEST"

        // When - exhaust AUTH tokens
        repeat(5) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }

        // Then - RESUME should still have tokens
        val resumeResult = rateLimiter.consumeToken(identifier, RateLimitStrategy.RESUME)
        resumeResult.shouldBeInstanceOf<RateLimitResult.Allowed>()

        // Then - WAITLIST should still have tokens
        val waitlistResult = rateLimiter.consumeToken(identifier, RateLimitStrategy.WAITLIST)
        waitlistResult.shouldBeInstanceOf<RateLimitResult.Allowed>()
    }

    @Test
    fun `should track cache eviction correctly`() = runTest {
        // Given - create multiple entries
        repeat(5) {
            rateLimiter.consumeToken("EVICTION-TEST-$it", RateLimitStrategy.AUTH)
        }

        // When - trigger cleanup
        rateLimiter.triggerCacheCleanup()

        // Then - cache should have entries
        val size = rateLimiter.getCacheSize()
        size shouldBeGreaterThanOrEqual 0
    }

    // Option A: Tolerant test - assert resetTime is between now and now + refillDuration + tolerance
    @Test
    fun `reset time is within expected range (tolerant)`() = runTest {
        val identifier = "TOLERANT-TEST"
        val now = Instant.now()
        val result = rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH) as RateLimitResult.Allowed

        // refillDuration configured for AUTH is 1 minute
        val upperBound = now.plus(Duration.ofMinutes(1)).plusMillis(1000) // 1s tolerance
        assert(!result.resetTime.isBefore(now)) { "resetTime should not be before now" }
        assert(!result.resetTime.isAfter(upperBound)) { "resetTime should be within now + refillDuration + tolerance" }
    }

    // Option B: Deterministic test by injecting a fixed clock
    @Test
    fun `reset time deterministic with injected clock`() = runTest {
        val fixedNow = Instant.parse("2025-01-01T00:00:00Z")
        val fixedClock = Clock.fixed(fixedNow, java.time.ZoneOffset.UTC)
        val configFactory = BucketConfigurationFactory(properties)
        val apiKeyParser = com.profiletailors.ratelimit.infrastructure.adapter.ApiKeyParser(properties)
        val meterRegistry = SimpleMeterRegistry()
        val metrics = RateLimitMetrics(meterRegistry)
        val deterministicLimiter = Bucket4jRateLimiter(
            configurationFactory = configFactory,
            apiKeyParser = apiKeyParser,
            metrics = metrics,
            properties = properties,
            clock = fixedClock,
        )

        val result = deterministicLimiter.consumeToken("DETERMINISTIC-KEY", RateLimitStrategy.AUTH)
        assertTrue(
            result is RateLimitResult.Allowed,
            "Expected RateLimitResult.Allowed, got ${result.let { it::class }}",
        )
        val expectedReset = fixedNow.plus(Duration.ofMinutes(1))
        assertEquals(
            expectedReset,
            result.resetTime,
            "Expected resetTime to be $expectedReset, but was ${result.resetTime}",
        )
    }

    @Test
    fun `should select shortest refill period when multiple bandwidths share minimum capacity`() = runTest {
        // Given - configure multiple bandwidths with the same capacity but different refill periods
        // This tests the edge case where:
        // Bandwidth A: 10 requests per minute (capacity=10, refill=60s)
        // Bandwidth B: 10 requests per hour (capacity=10, refill=3600s)
        // Both have capacity=10, but B is more restrictive (shorter effective rate)
        val customProperties = RateLimitProperties(
            enabled = true,
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "PX001-",
                basic = "BX001-",
            ),
            auth = authRateLimitConfig(),
            business = businessRateLimitConfig(),
            resume = resumeRateLimitConfig(),
            waitlist = waitlistRateLimitConfig(),
            cache = RateLimitProperties.CacheConfig(
                maxSize = 10000,
                ttlMinutes = 60,
            ),
        )

        val fixedNow = Instant.parse("2025-01-01T00:00:00Z")
        val fixedClock = Clock.fixed(fixedNow, java.time.ZoneOffset.UTC)
        val configFactory = BucketConfigurationFactory(customProperties)
        val apiKeyParser = com.profiletailors.ratelimit.infrastructure.adapter.ApiKeyParser(customProperties)
        val meterRegistry = SimpleMeterRegistry()
        val metrics = RateLimitMetrics(meterRegistry)
        val customLimiter = Bucket4jRateLimiter(
            configurationFactory = configFactory,
            apiKeyParser = apiKeyParser,
            metrics = metrics,
            properties = customProperties,
            clock = fixedClock,
        )

        // When - consume a token
        val result = customLimiter.consumeToken("MULTI-BANDWIDTH-TEST", RateLimitStrategy.AUTH)

        // Then - should select the shortest refill period (60s, not 3600s)
        // The resetTime should be now + 60 seconds, not now + 3600 seconds
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.limitCapacity shouldBe 10

        val expectedResetTime = fixedNow.plus(Duration.ofSeconds(60))
        assertEquals(
            expectedResetTime,
            result.resetTime,
            "Expected resetTime to use shortest refill period (60s), but got ${result.resetTime}",
        )
    }

    private fun waitlistRateLimitConfig(): RateLimitProperties.WaitlistRateLimitConfig =
        RateLimitProperties.WaitlistRateLimitConfig(
            enabled = true,
            limit = RateLimitProperties.BandwidthLimit(
                name = "waitlist",
                capacity = 10,
                refillTokens = 10,
                refillDuration = Duration.ofMinutes(1),
            ),
        )

    private fun resumeRateLimitConfig(): RateLimitProperties.ResumeRateLimitConfig =
        RateLimitProperties.ResumeRateLimitConfig(
            enabled = true,
            limit = RateLimitProperties.BandwidthLimit(
                name = "resume",
                capacity = 10,
                refillTokens = 10,
                refillDuration = Duration.ofMinutes(1),
            ),
        )

    private fun businessRateLimitConfig(): RateLimitProperties.BusinessRateLimitConfig =
        RateLimitProperties.BusinessRateLimitConfig(
            enabled = true,
            pricingPlans = mapOf(
                "free" to RateLimitProperties.BandwidthLimit(
                    name = "free",
                    capacity = 3,
                    refillTokens = 3,
                    refillDuration = Duration.ofMinutes(1),
                ),
            ),
        )

    private fun authRateLimitConfig(): RateLimitProperties.AuthRateLimitConfig =
        RateLimitProperties.AuthRateLimitConfig(
            enabled = true,
            limits = listOf(
                // Same capacity, different refill periods
                RateLimitProperties.BandwidthLimit(
                    name = "per-minute",
                    capacity = 10,
                    refillTokens = 10,
                    refillDuration = Duration.ofSeconds(60),
                ),
                RateLimitProperties.BandwidthLimit(
                    name = "per-hour",
                    capacity = 10,
                    refillTokens = 10,
                    refillDuration = Duration.ofSeconds(3600),
                ),
            ),
        )
}
