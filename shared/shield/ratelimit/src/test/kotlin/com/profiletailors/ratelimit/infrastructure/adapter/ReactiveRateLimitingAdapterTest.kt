package com.profiletailors.ratelimit.infrastructure.adapter

import com.profiletailors.ratelimit.application.RateLimitingService
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReactiveRateLimitingAdapterTest {

    private val service = mockk<RateLimitingService>()
    private val adapter = ReactiveRateLimitingAdapter(service)

    @Test
    fun `should consume token with default strategy`() {
        // Given
        val identifier = "test-id"
        val endpoint = "/test"
        val expectedResult = RateLimitResult.Allowed(10, 10, Instant.now())

        coEvery { service.consumeToken(identifier, endpoint) } returns expectedResult

        // When
        val result = adapter.consumeToken(identifier, endpoint)

        // Then
        StepVerifier.create(result)
            .expectNext(expectedResult)
            .verifyComplete()
    }

    @Test
    fun `should consume token with specific strategy`() {
        // Given
        val identifier = "test-id"
        val endpoint = "/test"
        val strategy = RateLimitStrategy.AUTH
        val expectedResult = RateLimitResult.Allowed(10, 10, Instant.now())

        coEvery { service.consumeToken(identifier, endpoint, strategy) } returns expectedResult

        // When
        val result = adapter.consumeToken(identifier, endpoint, strategy)

        // Then
        StepVerifier.create(result)
            .expectNext(expectedResult)
            .verifyComplete()
    }

    @Test
    fun `should propagate Denied result with default strategy`() {
        // Given
        val identifier = "rate-limited-id"
        val endpoint = "/api/data"
        val expectedResult = RateLimitResult.Denied(
            retryAfter = java.time.Duration.ofMinutes(1),
            limitCapacity = 100,
            windowDuration = java.time.Duration.ofHours(1),
        )

        coEvery { service.consumeToken(identifier, endpoint) } returns expectedResult

        // When
        val result = adapter.consumeToken(identifier, endpoint)

        // Then
        StepVerifier.create(result)
            .expectNext(expectedResult)
            .verifyComplete()
    }

    @Test
    fun `should propagate service exception as reactor error`() {
        // Given
        val identifier = "error-id"
        val endpoint = "/test"
        val error = RuntimeException("Service failure")

        coEvery { service.consumeToken(identifier, endpoint) } throws error

        // When
        val result = adapter.consumeToken(identifier, endpoint)

        // Then
        StepVerifier.create(result)
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `should consume token with RESUME strategy`() {
        // Given
        val identifier = "IP:10.0.0.1"
        val endpoint = "/api/resume/generate"
        val strategy = RateLimitStrategy.RESUME
        val expectedResult = RateLimitResult.Allowed(5, 10, Instant.now())

        coEvery { service.consumeToken(identifier, endpoint, strategy) } returns expectedResult

        // When
        val result = adapter.consumeToken(identifier, endpoint, strategy)

        // Then
        StepVerifier.create(result)
            .expectNext(expectedResult)
            .verifyComplete()
    }

    @Test
    fun `should consume token with WAITLIST strategy`() {
        // Given
        val identifier = "IP:10.0.0.2"
        val endpoint = "/api/waitlist/join"
        val strategy = RateLimitStrategy.WAITLIST
        val expectedResult = RateLimitResult.Denied(
            retryAfter = java.time.Duration.ofSeconds(30),
            limitCapacity = 10,
            windowDuration = java.time.Duration.ofMinutes(1),
        )

        coEvery { service.consumeToken(identifier, endpoint, strategy) } returns expectedResult

        // When
        val result = adapter.consumeToken(identifier, endpoint, strategy)

        // Then
        StepVerifier.create(result)
            .expectNext(expectedResult)
            .verifyComplete()
    }
}
