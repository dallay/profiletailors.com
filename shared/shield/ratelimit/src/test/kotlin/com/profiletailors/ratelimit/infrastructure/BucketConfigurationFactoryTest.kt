package com.profiletailors.ratelimit.infrastructure

import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Unit tests for BucketConfigurationFactory.
 *
 * Tests cover:
 * - Auth bucket configuration creation
 * - Business bucket configuration creation
 * - Resume bucket configuration creation
 * - Waitlist bucket configuration creation
 * - Multiple limit handling
 * - Invalid plan handling
 * - Enable/disable flags
 * - Custom refill rates (refillTokens != capacity)
 */

class BucketConfigurationFactoryTest {

    private lateinit var factory: BucketConfigurationFactory
    private lateinit var properties: RateLimitProperties

    @BeforeEach
    fun setUp() {
        properties = RateLimitProperties(
            enabled = true,
            auth = RateLimitProperties.AuthRateLimitConfig(
                enabled = true,
                endpoints = listOf("/api/auth/login", "/api/auth/register"),
                limits = listOf(
                    RateLimitProperties.BandwidthLimit(
                        name = "per-minute",
                        capacity = 10,
                        refillTokens = 10,
                        refillDuration = Duration.ofMinutes(1),
                    ),
                    RateLimitProperties.BandwidthLimit(
                        name = "per-hour",
                        capacity = 100,
                        refillTokens = 100,
                        refillDuration = Duration.ofHours(1),
                    ),
                ),
            ),
            business = RateLimitProperties.BusinessRateLimitConfig(
                enabled = true,
                pricingPlans = mapOf(
                    "free" to RateLimitProperties.BandwidthLimit(
                        name = "free-plan",
                        capacity = 20,
                        refillTokens = 20,
                        refillDuration = Duration.ofHours(1),
                    ),
                    "basic" to RateLimitProperties.BandwidthLimit(
                        name = "basic-plan",
                        capacity = 40,
                        refillTokens = 40,
                        refillDuration = Duration.ofHours(1),
                    ),
                    "professional" to RateLimitProperties.BandwidthLimit(
                        name = "professional-plan",
                        capacity = 100,
                        refillTokens = 100,
                        refillDuration = Duration.ofHours(1),
                    ),
                ),
            ),
            resume = RateLimitProperties.ResumeRateLimitConfig(
                enabled = true,
                endpoints = listOf("/api/resume/generate"),
                limit = RateLimitProperties.BandwidthLimit(
                    name = "resume-limit",
                    capacity = 10,
                    refillTokens = 10,
                    refillDuration = Duration.ofMinutes(1),
                ),
            ),
            waitlist = RateLimitProperties.WaitlistRateLimitConfig(
                enabled = true,
                endpoints = listOf("/api/waitlist/join"),
                limit = RateLimitProperties.BandwidthLimit(
                    name = "waitlist-limit",
                    capacity = 5,
                    refillTokens = 5,
                    refillDuration = Duration.ofMinutes(1),
                ),
            ),
        )
        factory = BucketConfigurationFactory(properties)
    }

    @Test
    fun `should create auth bucket configuration with multiple limits`() {
        // When
        val config = factory.createConfiguration(RateLimitStrategy.AUTH)

        // Then
        config.bandwidths.size shouldBe 2
    }

    @Test
    fun `should create business bucket configuration for free plan`() {
        // When
        val config = factory.createConfiguration(RateLimitStrategy.BUSINESS, "free")

        // Then
        config.bandwidths.size shouldBe 1
    }

    @Test
    fun `should create business bucket configuration for basic plan`() {
        // When
        val config = factory.createConfiguration(RateLimitStrategy.BUSINESS, "basic")

        // Then
        config.bandwidths.size shouldBe 1
    }

    @Test
    fun `should create business bucket configuration for professional plan`() {
        // When
        val config = factory.createConfiguration(RateLimitStrategy.BUSINESS, "professional")

        // Then
        config.bandwidths.size shouldBe 1
    }

    @Test
    fun `should be case-insensitive for plan names`() {
        // When
        val config1 = factory.createConfiguration(RateLimitStrategy.BUSINESS, "FREE")
        val config2 = factory.createConfiguration(RateLimitStrategy.BUSINESS, "free")
        val config3 = factory.createConfiguration(RateLimitStrategy.BUSINESS, "Free")

        // Then - all should succeed
        config1.bandwidths.size shouldBe 1
        config2.bandwidths.size shouldBe 1
        config3.bandwidths.size shouldBe 1
    }

    @Test
    fun `should throw exception for unknown pricing plan`() {
        // When/Then
        val exception = shouldThrow<IllegalArgumentException> {
            factory.createConfiguration(RateLimitStrategy.BUSINESS, "unknown-plan")
        }

        exception.message shouldContain "Unknown pricing plan"
        exception.message shouldContain "unknown-plan"
        exception.message shouldContain "free"
        exception.message shouldContain "basic"
        exception.message shouldContain "professional"
    }

    @Test
    fun `should return auth endpoints list`() {
        // When
        val endpoints = factory.getEndpoints(RateLimitStrategy.AUTH)

        // Then
        endpoints.size shouldBe 2
        endpoints shouldContain "/api/auth/login"
        endpoints shouldContain "/api/auth/register"
    }

    @Test
    fun `should return true when auth rate limiting is fully enabled`() {
        // When
        val isEnabled = factory.isRateLimitEnabled(RateLimitStrategy.AUTH)

        // Then
        isEnabled shouldBe true
    }

    @Test
    fun `should return false when auth rate limiting is disabled globally`() {
        // Given
        val disabledProperties = properties.copy(enabled = false)
        val disabledStrategy = BucketConfigurationFactory(disabledProperties)

        // When
        val isEnabled = disabledStrategy.isRateLimitEnabled(RateLimitStrategy.AUTH)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should return false when auth rate limiting is disabled specifically`() {
        // Given
        val disabledAuthProperties = properties.copy(
            auth = properties.auth.copy(enabled = false),
        )
        val disabledAuthStrategy = BucketConfigurationFactory(disabledAuthProperties)

        // When
        val isEnabled = disabledAuthStrategy.isRateLimitEnabled(RateLimitStrategy.AUTH)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should return true when business rate limiting is fully enabled`() {
        // When
        val isEnabled = factory.isRateLimitEnabled(RateLimitStrategy.BUSINESS)

        // Then
        isEnabled shouldBe true
    }

    @Test
    fun `should return false when business rate limiting is disabled globally`() {
        // Given
        val disabledProperties = properties.copy(enabled = false)
        val disabledStrategy = BucketConfigurationFactory(disabledProperties)

        // When
        val isEnabled = disabledStrategy.isRateLimitEnabled(RateLimitStrategy.BUSINESS)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should return false when business rate limiting is disabled specifically`() {
        // Given
        val disabledBusinessProperties = properties.copy(
            business = properties.business.copy(enabled = false),
        )
        val disabledBusinessStrategy = BucketConfigurationFactory(disabledBusinessProperties)

        // When
        val isEnabled = disabledBusinessStrategy.isRateLimitEnabled(RateLimitStrategy.BUSINESS)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should handle empty auth limits list gracefully`() {
        // Given
        val emptyLimitsProperties = properties.copy(
            auth = properties.auth.copy(limits = emptyList()),
        )
        val emptyLimitsStrategy = BucketConfigurationFactory(emptyLimitsProperties)

        // When/Then - Bucket4j doesn't allow empty configuration, so it should throw
        shouldThrow<IllegalArgumentException> {
            emptyLimitsStrategy.createConfiguration(RateLimitStrategy.AUTH)
        }
    }

    @Test
    fun `should handle single auth limit`() {
        // Given
        val singleLimitProperties = properties.copy(
            auth = properties.auth.copy(
                limits = listOf(
                    RateLimitProperties.BandwidthLimit(
                        name = "per-minute",
                        capacity = 10,
                        refillTokens = 10,
                        refillDuration = Duration.ofMinutes(1),
                    ),
                ),
            ),
        )
        val singleLimitStrategy = BucketConfigurationFactory(singleLimitProperties)

        // When
        val config = singleLimitStrategy.createConfiguration(RateLimitStrategy.AUTH)

        // Then
        config.bandwidths.size shouldBe 1
    }

    @Test
    fun `should create bucket with custom initial tokens`() {
        // Given
        val customProperties = properties.copy(
            business = RateLimitProperties.BusinessRateLimitConfig(
                enabled = true,
                pricingPlans = mapOf(
                    "custom" to RateLimitProperties.BandwidthLimit(
                        name = "custom-plan",
                        capacity = 100,
                        refillTokens = 50,
                        refillDuration = Duration.ofHours(1),
                        initialTokens = 10,
                    ),
                ),
            ),
        )
        val customStrategy = BucketConfigurationFactory(customProperties)

        // When
        val config = customStrategy.createConfiguration(RateLimitStrategy.BUSINESS, "custom")

        // Then
        config.bandwidths.size shouldBe 1
    }

    @Test
    fun `should create bandwidth with refillTokens equal to capacity`() {
        // Given - properties already have refillTokens == capacity in setUp()
        val authConfig = factory.createConfiguration(RateLimitStrategy.AUTH)

        // When
        val bandwidth = authConfig.bandwidths[0]

        // Then - verify bandwidth was created with simple refill (greedy)
        bandwidth.capacity shouldBe 10
    }

    @Test
    fun `should create bandwidth with custom refill rate when refillTokens differs from capacity`() {
        // Given
        val customLimit = RateLimitProperties.BandwidthLimit(
            name = "custom-refill",
            capacity = 100,
            refillTokens = 10,
            refillDuration = Duration.ofMinutes(1),
        )
        val customProperties = properties.copy(
            business = RateLimitProperties.BusinessRateLimitConfig(
                enabled = true,
                pricingPlans = mapOf(
                    "custom" to customLimit,
                ),
            ),
        )
        val customStrategy = BucketConfigurationFactory(customProperties)

        // When
        val config = customStrategy.createConfiguration(RateLimitStrategy.BUSINESS, "custom")

        // Then - verify bandwidth was created with custom refill
        config.bandwidths.size shouldBe 1
        config.bandwidths[0].capacity shouldBe 100
    }

    @Test
    fun `should handle invalid refill durations gracefully`() {
        // Given
        val invalidLimit = RateLimitProperties.BandwidthLimit(
            name = "invalid-duration",
            capacity = 30,
            refillTokens = 20,
            refillDuration = Duration.ZERO,
        )
        val invalidProperties = properties.copy(
            auth = properties.auth.copy(limits = listOf(invalidLimit)),
        )
        val invalidStrategy = BucketConfigurationFactory(invalidProperties)

        // When/Then - Bucket4j should throw when duration is zero
        shouldThrow<IllegalArgumentException> {
            invalidStrategy.createConfiguration(RateLimitStrategy.AUTH)
        }
    }

    // --- RESUME strategy tests ---

    @Test
    fun `should create resume bucket configuration`() {
        // When
        val config = factory.createConfiguration(RateLimitStrategy.RESUME)

        // Then
        config.bandwidths.size shouldBe 1
        config.bandwidths[0].capacity shouldBe 10
    }

    @Test
    fun `should return true when resume rate limiting is enabled`() {
        // When
        val isEnabled = factory.isRateLimitEnabled(RateLimitStrategy.RESUME)

        // Then
        isEnabled shouldBe true
    }

    @Test
    fun `should return false when resume rate limiting is disabled globally`() {
        // Given
        val disabledProperties = properties.copy(enabled = false)
        val disabledFactory = BucketConfigurationFactory(disabledProperties)

        // When
        val isEnabled = disabledFactory.isRateLimitEnabled(RateLimitStrategy.RESUME)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should return false when resume rate limiting is disabled specifically`() {
        // Given
        val disabledResumeProperties = properties.copy(
            resume = properties.resume.copy(enabled = false),
        )
        val disabledFactory = BucketConfigurationFactory(disabledResumeProperties)

        // When
        val isEnabled = disabledFactory.isRateLimitEnabled(RateLimitStrategy.RESUME)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should return resume endpoints list`() {
        // When
        val endpoints = factory.getEndpoints(RateLimitStrategy.RESUME)

        // Then
        endpoints shouldContainExactly listOf("/api/resume/generate")
    }

    // --- WAITLIST strategy tests ---

    @Test
    fun `should create waitlist bucket configuration`() {
        // When
        val config = factory.createConfiguration(RateLimitStrategy.WAITLIST)

        // Then
        config.bandwidths.size shouldBe 1
        config.bandwidths[0].capacity shouldBe 5
    }

    @Test
    fun `should return true when waitlist rate limiting is enabled`() {
        // When
        val isEnabled = factory.isRateLimitEnabled(RateLimitStrategy.WAITLIST)

        // Then
        isEnabled shouldBe true
    }

    @Test
    fun `should return false when waitlist rate limiting is disabled globally`() {
        // Given
        val disabledProperties = properties.copy(enabled = false)
        val disabledFactory = BucketConfigurationFactory(disabledProperties)

        // When
        val isEnabled = disabledFactory.isRateLimitEnabled(RateLimitStrategy.WAITLIST)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should return false when waitlist rate limiting is disabled specifically`() {
        // Given
        val disabledWaitlistProperties = properties.copy(
            waitlist = properties.waitlist.copy(enabled = false),
        )
        val disabledFactory = BucketConfigurationFactory(disabledWaitlistProperties)

        // When
        val isEnabled = disabledFactory.isRateLimitEnabled(RateLimitStrategy.WAITLIST)

        // Then
        isEnabled shouldBe false
    }

    @Test
    fun `should return waitlist endpoints list`() {
        // When
        val endpoints = factory.getEndpoints(RateLimitStrategy.WAITLIST)

        // Then
        endpoints shouldContainExactly listOf("/api/waitlist/join")
    }

    // --- getEndpoints tests ---

    @Test
    fun `should return empty list for BUSINESS strategy endpoints`() {
        // When
        val endpoints = factory.getEndpoints(RateLimitStrategy.BUSINESS)

        // Then
        endpoints shouldContainExactly emptyList()
    }

    // --- createConfiguration overload tests ---

    @Test
    fun `should use default free plan when business strategy called without plan name`() {
        // When
        val config = factory.createConfiguration(RateLimitStrategy.BUSINESS)

        // Then
        config.bandwidths.size shouldBe 1
        config.bandwidths[0].capacity shouldBe 20 // free plan capacity
    }
}
