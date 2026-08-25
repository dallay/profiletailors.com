package com.profiletailors.ratelimit.infrastructure.gateway

import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.domain.event.RateLimitExceededEvent
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant

/**
 * Unit tests for [SpringRateLimitEventPublisher].
 */

@OptIn(ExperimentalCoroutinesApi::class)
class SpringRateLimitEventPublisherTest {

    private lateinit var applicationEventPublisher: ApplicationEventPublisher
    private lateinit var publisher: SpringRateLimitEventPublisher

    @BeforeEach
    fun setUp() {
        applicationEventPublisher = mockk(relaxed = true)
        publisher = SpringRateLimitEventPublisher(applicationEventPublisher)
    }

    @Test
    fun `should publish event successfully`() = runTest {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "test-id",
            endpoint = "/test",
            attemptCount = 5,
            maxAttempts = 5,
            windowDuration = Duration.ofMinutes(1),
            strategy = RateLimitStrategy.AUTH,
            timestamp = Instant.now(),
            resetTime = Instant.now().plus(Duration.ofMinutes(1)),
        )

        // When
        publisher.publish(event)

        // Then
        verify(exactly = 1) { applicationEventPublisher.publishEvent(event) }
    }

    @Test
    fun `should propagate IllegalArgumentException when event publishing fails`() = runTest {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "test-id",
            endpoint = "/test",
            attemptCount = 5,
            maxAttempts = 5,
            windowDuration = Duration.ofMinutes(1),
            strategy = RateLimitStrategy.AUTH,
            timestamp = Instant.now(),
            resetTime = Instant.now().plus(Duration.ofMinutes(1)),
        )
        val exception = IllegalArgumentException("Publishing failed")
        every { applicationEventPublisher.publishEvent(event) } throws exception

        // When/Then
        shouldThrow<IllegalArgumentException> {
            publisher.publish(event)
        }

        verify(exactly = 1) { applicationEventPublisher.publishEvent(event) }
    }

    @Test
    fun `should propagate IllegalStateException when event publishing fails`() = runTest {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "test-id",
            endpoint = "/test",
            attemptCount = 5,
            maxAttempts = 5,
            windowDuration = Duration.ofMinutes(1),
            strategy = RateLimitStrategy.AUTH,
            timestamp = Instant.now(),
            resetTime = Instant.now().plus(Duration.ofMinutes(1)),
        )
        val exception = IllegalStateException("Publishing failed")
        every { applicationEventPublisher.publishEvent(event) } throws exception

        // When/Then
        shouldThrow<IllegalStateException> {
            publisher.publish(event)
        }

        verify(exactly = 1) { applicationEventPublisher.publishEvent(event) }
    }
}
