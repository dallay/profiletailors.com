package com.profiletailors.ratelimit.domain

import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.domain.event.RateLimitExceededEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * Unit tests for RateLimitExceededEvent domain event.
 *
 * Tests cover:
 * - Event creation and validation
 * - Required field constraints
 * - Description formatting
 * - Time until reset calculation
 */

class RateLimitExceededEventTest {

    @Test
    fun `should create valid event with all fields`() {
        // Given
        val identifier = "IP:192.168.1.1"
        val endpoint = "/api/auth/login"
        val attemptCount = 11
        val maxAttempts = 10
        val windowDuration = Duration.ofMinutes(5)
        val timestamp = Instant.now()
        val resetTime = timestamp.plus(windowDuration)

        // When
        val event = RateLimitExceededEvent(
            identifier = identifier,
            endpoint = endpoint,
            attemptCount = attemptCount,
            maxAttempts = maxAttempts,
            windowDuration = windowDuration,
            strategy = RateLimitStrategy.AUTH,
            timestamp = timestamp,
            resetTime = resetTime,
        )

        // Then
        event.identifier shouldBe identifier
        event.endpoint shouldBe endpoint
        event.attemptCount shouldBe attemptCount
        event.maxAttempts shouldBe maxAttempts
        event.windowDuration shouldBe windowDuration
        event.strategy shouldBe RateLimitStrategy.AUTH
        event.timestamp shouldBe timestamp
        event.resetTime shouldBe resetTime
    }

    @Test
    fun `should create valid event with null attempt counts`() {
        // Given
        val identifier = "API:test-key"
        val endpoint = "/api/business/data"

        // When
        val event = RateLimitExceededEvent(
            identifier = identifier,
            endpoint = endpoint,
            attemptCount = null,
            maxAttempts = null,
            windowDuration = Duration.ofHours(1),
            strategy = RateLimitStrategy.BUSINESS,
        )

        // Then
        event.identifier shouldBe identifier
        event.endpoint shouldBe endpoint
        event.attemptCount shouldBe null
        event.maxAttempts shouldBe null
        event.strategy shouldBe RateLimitStrategy.BUSINESS
    }

    @Test
    fun `should reject blank identifier`() {
        // When/Then
        shouldThrow<IllegalArgumentException> {
            RateLimitExceededEvent(
                identifier = "",
                endpoint = "/api/test",
                attemptCount = null,
                maxAttempts = null,
                windowDuration = Duration.ofMinutes(1),
                strategy = RateLimitStrategy.AUTH,
            )
        }

        shouldThrow<IllegalArgumentException> {
            RateLimitExceededEvent(
                identifier = "   ",
                endpoint = "/api/test",
                attemptCount = null,
                maxAttempts = null,
                windowDuration = Duration.ofMinutes(1),
                strategy = RateLimitStrategy.AUTH,
            )
        }
    }

    @Test
    fun `should reject blank endpoint`() {
        // When/Then
        shouldThrow<IllegalArgumentException> {
            RateLimitExceededEvent(
                identifier = "test-id",
                endpoint = "",
                attemptCount = null,
                maxAttempts = null,
                windowDuration = Duration.ofMinutes(1),
                strategy = RateLimitStrategy.AUTH,
            )
        }
    }

    @Test
    fun `should reject zero or negative attempt count`() {
        // When/Then
        shouldThrow<IllegalArgumentException> {
            RateLimitExceededEvent(
                identifier = "test-id",
                endpoint = "/api/test",
                attemptCount = 0,
                maxAttempts = 10,
                windowDuration = Duration.ofMinutes(1),
                strategy = RateLimitStrategy.AUTH,
            )
        }

        shouldThrow<IllegalArgumentException> {
            RateLimitExceededEvent(
                identifier = "test-id",
                endpoint = "/api/test",
                attemptCount = -1,
                maxAttempts = 10,
                windowDuration = Duration.ofMinutes(1),
                strategy = RateLimitStrategy.AUTH,
            )
        }
    }

    @Test
    fun `should reject zero or negative max attempts`() {
        // When/Then
        shouldThrow<IllegalArgumentException> {
            RateLimitExceededEvent(
                identifier = "test-id",
                endpoint = "/api/test",
                attemptCount = 10,
                maxAttempts = 0,
                windowDuration = Duration.ofMinutes(1),
                strategy = RateLimitStrategy.AUTH,
            )
        }
    }

    @Test
    fun `should reject attempt count less than max attempts`() {
        // When/Then
        shouldThrow<IllegalArgumentException> {
            RateLimitExceededEvent(
                identifier = "test-id",
                endpoint = "/api/test",
                attemptCount = 5,
                maxAttempts = 10,
                windowDuration = Duration.ofMinutes(1),
                strategy = RateLimitStrategy.AUTH,
            )
        }
    }

    @Test
    fun `should accept attempt count equal to max attempts`() {
        // When
        val event = RateLimitExceededEvent(
            identifier = "test-id",
            endpoint = "/api/test",
            attemptCount = 10,
            maxAttempts = 10,
            windowDuration = Duration.ofMinutes(1),
            strategy = RateLimitStrategy.AUTH,
        )

        // Then
        event.attemptCount shouldBe 10
        event.maxAttempts shouldBe 10
    }

    @Test
    fun `should generate description with attempt counts`() {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "IP:192.168.1.1",
            endpoint = "/api/auth/login",
            attemptCount = 11,
            maxAttempts = 10,
            windowDuration = Duration.ofMinutes(5),
            strategy = RateLimitStrategy.AUTH,
        )

        // When
        val description = event.describe()

        // Then
        description shouldContain "IP:192.168.1.1"
        description shouldContain "/api/auth/login"
        description shouldContain "11/10"
    }

    @Test
    fun `should generate description without attempt counts`() {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "API:test-key",
            endpoint = "/api/business/data",
            attemptCount = null,
            maxAttempts = null,
            windowDuration = Duration.ofHours(1),
            strategy = RateLimitStrategy.BUSINESS,
        )

        // When
        val description = event.describe()

        // Then
        description shouldContain "API:test-key"
        description shouldContain "/api/business/data"
        description shouldContain "Rate limit exceeded"
    }

    @Test
    fun `should calculate time until reset correctly`() {
        // Given
        val now = Instant.now()
        val resetTime = now.plusSeconds(300) // 5 minutes
        val event = RateLimitExceededEvent(
            identifier = "test-id",
            endpoint = "/api/test",
            attemptCount = null,
            maxAttempts = null,
            windowDuration = Duration.ofMinutes(5),
            strategy = RateLimitStrategy.BUSINESS,
            timestamp = now,
            resetTime = resetTime,
        )

        // When
        val timeUntilReset = event.timeUntilReset()

        // Then
        timeUntilReset shouldNotBe null
        timeUntilReset!!.seconds shouldBe 300
    }

    @Test
    fun `should correctly store and retrieve AUTH strategy`() {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "IP:192.168.1.1",
            endpoint = "/api/auth/login",
            attemptCount = 11,
            maxAttempts = 10,
            windowDuration = Duration.ofMinutes(5),
            strategy = RateLimitStrategy.AUTH,
        )

        // Then
        event.strategy shouldBe RateLimitStrategy.AUTH
    }

    @Test
    fun `should correctly store and retrieve BUSINESS strategy`() {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "API:test-key",
            endpoint = "/api/business/data",
            attemptCount = null,
            maxAttempts = null,
            windowDuration = Duration.ofHours(1),
            strategy = RateLimitStrategy.BUSINESS,
        )

        // Then
        event.strategy shouldBe RateLimitStrategy.BUSINESS
    }

    @Test
    fun `should return null for time until reset when reset time is null`() {
        // Given
        val event = RateLimitExceededEvent(
            identifier = "test-id",
            endpoint = "/api/test",
            attemptCount = null,
            maxAttempts = null,
            windowDuration = Duration.ofMinutes(5),
            strategy = RateLimitStrategy.BUSINESS,
            resetTime = null,
        )

        // When
        val timeUntilReset = event.timeUntilReset()

        // Then
        timeUntilReset shouldBe null
    }

    @Test
    fun `should set default timestamp if not provided`() {
        // Given
        val before = Instant.now()

        // When
        val event = RateLimitExceededEvent(
            identifier = "test-id",
            endpoint = "/api/test",
            attemptCount = null,
            maxAttempts = null,
            windowDuration = Duration.ofMinutes(5),
            strategy = RateLimitStrategy.BUSINESS,
        )

        val after = Instant.now()

        // Then
        event.timestamp shouldNotBe null
        !event.timestamp.isBefore(before) shouldBe true
        !event.timestamp.isAfter(after) shouldBe true
    }
}
