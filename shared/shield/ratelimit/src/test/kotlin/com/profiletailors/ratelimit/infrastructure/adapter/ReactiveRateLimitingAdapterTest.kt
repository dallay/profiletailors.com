package com.profiletailors.ratelimit.infrastructure.adapter

import com.profiletailors.ratelimit.application.RateLimitingService
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

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
}
