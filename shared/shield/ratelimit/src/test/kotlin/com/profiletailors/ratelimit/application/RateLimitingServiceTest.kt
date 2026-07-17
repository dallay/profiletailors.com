package com.profiletailors.ratelimit.application

import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.domain.RateLimiter
import com.profiletailors.ratelimit.domain.event.RateLimitExceededEvent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimitingServiceTest {

    private lateinit var rateLimiter: RateLimiter
    private lateinit var eventPublisher: EventPublisher<RateLimitExceededEvent>
    private lateinit var service: RateLimitingService

    @BeforeEach
    fun setUp() {
        rateLimiter = mockk()
        eventPublisher = mockk()
        service = RateLimitingService(rateLimiter, eventPublisher)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should consume token with default BUSINESS strategy`() = runTest {
        // Given
        val identifier = "API:test-key"
        val endpoint = "/api/business/data"
        val expectedResult = RateLimitResult.Allowed(
            remainingTokens = 99,
            limitCapacity = 100,
            resetTime = Instant.now().plusSeconds(3600),
        )

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        } returns expectedResult

        // When
        val result = service.consumeToken(identifier, endpoint)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 99

        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        }
    }

    @Test
    fun `should consume token with specific AUTH strategy`() = runTest {
        // Given
        val identifier = "IP:192.168.1.1"
        val endpoint = "/api/auth/login"
        val expectedResult = RateLimitResult.Allowed(
            remainingTokens = 9,
            limitCapacity = 10,
            resetTime = Instant.now().plusSeconds(60),
        )

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        } returns expectedResult

        // When
        val result = service.consumeToken(identifier, endpoint, RateLimitStrategy.AUTH)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 9

        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }
    }

    @Test
    fun `should publish event when rate limit is exceeded`() = runTest {
        // Given
        val identifier = "IP:192.168.1.2"
        val endpoint = "/api/auth/login"
        val retryAfter = Duration.ofMinutes(5)
        val expectedResult = RateLimitResult.Denied(
            retryAfter = retryAfter,
            limitCapacity = 10,
            windowDuration = Duration.ofMinutes(1),
        )

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        } returns expectedResult

        coEvery {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        } just Runs

        // When
        val result = service.consumeToken(identifier, endpoint, RateLimitStrategy.AUTH)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        result.retryAfter shouldBe retryAfter

        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }
        coVerify(exactly = 1) {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        }
    }

    @Test
    fun `should not publish event when rate limit is not exceeded`() = runTest {
        // Given
        val identifier = "IP:192.168.1.3"
        val endpoint = "/api/auth/login"
        val expectedResult = RateLimitResult.Allowed(
            remainingTokens = 5,
            limitCapacity = 10,
            resetTime = Instant.now().plusSeconds(60),
        )

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        } returns expectedResult

        // When
        service.consumeToken(identifier, endpoint, RateLimitStrategy.AUTH)

        // Then
        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }
        coVerify(exactly = 0) {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        }
    }

    @Test
    fun `should publish event with BUSINESS strategy when limit exceeded`() = runTest {
        // Given
        val identifier = "API:test-key"
        val endpoint = "/api/business/data"
        val retryAfter = Duration.ofHours(1)
        val expectedResult = RateLimitResult.Denied(
            retryAfter = retryAfter,
            limitCapacity = 100,
            windowDuration = Duration.ofHours(1),
        )

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        } returns expectedResult

        coEvery {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        } just Runs

        // When
        val result = service.consumeToken(identifier, endpoint, RateLimitStrategy.BUSINESS)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Denied>()

        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        }
        coVerify(exactly = 1) {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        }
    }

    @Test
    fun `should handle multiple consecutive allowed requests`() = runTest {
        // Given
        val identifier = "API:test-key"
        val endpoint = "/api/business/data"
        val resetTime = Instant.now().plusSeconds(3600)

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        } returnsMany listOf(
            RateLimitResult.Allowed(
                remainingTokens = 10,
                limitCapacity = 100,
                resetTime = resetTime,
            ),
            RateLimitResult.Allowed(
                remainingTokens = 9,
                limitCapacity = 100,
                resetTime = resetTime,
            ),
            RateLimitResult.Allowed(
                remainingTokens = 8,
                limitCapacity = 100,
                resetTime = resetTime,
            ),
        )

        // When - First request
        val result1 = service.consumeToken(identifier, endpoint)
        result1.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result1.remainingTokens shouldBe 10

        // When - Second request
        val result2 = service.consumeToken(identifier, endpoint)
        result2.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result2.remainingTokens shouldBe 9

        // When - Third request
        val result3 = service.consumeToken(identifier, endpoint)
        result3.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result3.remainingTokens shouldBe 8

        // Then
        coVerify(exactly = 3) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        }
    }

    @Test
    fun `should handle transition from allowed to denied`() = runTest {
        // Given
        val identifier = "API:test-key"
        val endpoint = "/api/business/data"
        val retryAfter = Duration.ofHours(1)

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        } returnsMany listOf(
            RateLimitResult.Allowed(
                remainingTokens = 1,
                limitCapacity = 100,
                resetTime = Instant.now().plusSeconds(3600),
            ),
            RateLimitResult.Denied(
                retryAfter = retryAfter,
                limitCapacity = 100,
                windowDuration = Duration.ofHours(1),
            ),
        )

        coEvery {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        } just Runs

        // When - First request (allowed)
        val result1 = service.consumeToken(identifier, endpoint)
        result1.shouldBeInstanceOf<RateLimitResult.Allowed>()

        // When - Second request (denied)
        val result2 = service.consumeToken(identifier, endpoint)
        result2.shouldBeInstanceOf<RateLimitResult.Denied>()
        result2.retryAfter shouldBe retryAfter

        // Then
        coVerify(exactly = 2) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        }
        coVerify(exactly = 1) {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        }
    }

    @Test
    fun `should propagate errors from rate limiter`() = runTest {
        // Given
        val identifier = "API:test-key"
        val endpoint = "/api/business/data"
        val error = RuntimeException("Rate limiter error")

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        } throws error

        // When/Then
        assertThrows<RuntimeException> {
            service.consumeToken(identifier, endpoint)
        }
    }

    @Test
    fun `should handle different identifiers independently`() = runTest {
        // Given
        val identifier1 = "API:key-1"
        val identifier2 = "API:key-2"
        val endpoint = "/api/business/data"

        coEvery {
            rateLimiter.consumeToken(identifier1, RateLimitStrategy.BUSINESS)
        } returns RateLimitResult.Allowed(
            remainingTokens = 10,
            limitCapacity = 100,
            resetTime = Instant.now().plusSeconds(3600),
        )

        coEvery {
            rateLimiter.consumeToken(identifier2, RateLimitStrategy.BUSINESS)
        } returns RateLimitResult.Denied(
            retryAfter = Duration.ofHours(1),
            limitCapacity = 100,
            windowDuration = Duration.ofHours(1),
        )

        coEvery {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        } just Runs

        // When - identifier1 (allowed)
        val result1 = service.consumeToken(identifier1, endpoint)
        result1.shouldBeInstanceOf<RateLimitResult.Allowed>()

        // When - identifier2 (denied)
        val result2 = service.consumeToken(identifier2, endpoint)
        result2.shouldBeInstanceOf<RateLimitResult.Denied>()

        // Then
        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier1, RateLimitStrategy.BUSINESS)
        }
        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier2, RateLimitStrategy.BUSINESS)
        }
    }

    @Test
    fun `should handle event publisher error gracefully`() = runTest {
        // Given
        val identifier = "IP:192.168.1.4"
        val endpoint = "/api/auth/login"
        val expectedResult = RateLimitResult.Denied(
            retryAfter = Duration.ofMinutes(1),
            limitCapacity = 10,
            windowDuration = Duration.ofMinutes(1),
        )

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        } returns expectedResult

        coEvery {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        } throws RuntimeException("Event publishing failed")

        // When
        val result = service.consumeToken(identifier, endpoint, RateLimitStrategy.AUTH)

        // Then
        result shouldBe expectedResult
        coVerify(exactly = 1) { eventPublisher.publish(any<RateLimitExceededEvent>()) }
    }

    @Test
    fun `should publish event with correct endpoint when RESUME strategy limit exceeded`() = runTest {
        // Given
        val identifier = "IP:10.0.0.5"
        val endpoint = "/api/resume/generate"
        val expectedResult = RateLimitResult.Denied(
            retryAfter = Duration.ofMinutes(1),
            limitCapacity = 10,
            windowDuration = Duration.ofMinutes(1),
        )

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.RESUME)
        } returns expectedResult

        coEvery {
            eventPublisher.publish(any<RateLimitExceededEvent>())
        } just Runs

        // When
        val result = service.consumeToken(identifier, endpoint, RateLimitStrategy.RESUME)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        coVerify(exactly = 1) { eventPublisher.publish(any<RateLimitExceededEvent>()) }
    }

    @Test
    fun `should publish event with WAITLIST strategy when limit exceeded`() = runTest {
        // Given
        val identifier = "IP:10.0.0.6"
        val endpoint = "/api/waitlist/join"
        val expectedResult = RateLimitResult.Denied(
            retryAfter = Duration.ofSeconds(30),
            limitCapacity = 10,
            windowDuration = Duration.ofMinutes(1),
        )
        val publishedEvent = slot<RateLimitExceededEvent>()

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.WAITLIST, "$identifier:$endpoint")
        } returns expectedResult

        coEvery {
            eventPublisher.publish(capture(publishedEvent))
        } just Runs

        // When
        val result = service.consumeToken(identifier, endpoint, RateLimitStrategy.WAITLIST)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Denied>()
        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.WAITLIST, "$identifier:$endpoint")
        }
        coVerify(exactly = 1) { eventPublisher.publish(any<RateLimitExceededEvent>()) }
        publishedEvent.captured.identifier shouldBe identifier
    }

    @Test
    fun `should use BUSINESS strategy when no strategy specified`() = runTest {
        // Given
        val identifier = "API:some-key"
        val endpoint = "/api/data"

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        } returns RateLimitResult.Allowed(
            remainingTokens = 50,
            limitCapacity = 100,
            resetTime = Instant.now().plusSeconds(3600),
        )

        // When
        val result = service.consumeToken(identifier, endpoint)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        coVerify(exactly = 0) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.AUTH)
        }
        coVerify(exactly = 1) {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        }
    }

    @Test
    fun `should return Allowed result without publishing event`() = runTest {
        // Given - zero-remaining tokens still allowed (last token consumed)
        val identifier = "API:last-token"
        val endpoint = "/api/business/data"

        coEvery {
            rateLimiter.consumeToken(identifier, RateLimitStrategy.BUSINESS)
        } returns RateLimitResult.Allowed(
            remainingTokens = 0,
            limitCapacity = 100,
            resetTime = Instant.now().plusSeconds(3600),
        )

        // When
        val result = service.consumeToken(identifier, endpoint)

        // Then
        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remainingTokens shouldBe 0
        coVerify(exactly = 0) { eventPublisher.publish(any<RateLimitExceededEvent>()) }
    }
}
