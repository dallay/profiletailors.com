package com.profiletailors.ratelimit.infrastructure.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class RateLimitPropertiesTest {

    @Test
    fun `should have correct default values`() {
        val properties = RateLimitProperties()

        properties.enabled shouldBe true
        properties.cache.maxSize shouldBe 10000
        properties.cache.ttlMinutes shouldBe 60

        properties.auth.enabled shouldBe true
        properties.business.enabled shouldBe true
        properties.resume.enabled shouldBe true
        properties.waitlist.enabled shouldBe true
    }

    @Test
    fun `should validate cache configuration`() {
        shouldThrow<IllegalArgumentException> {
            RateLimitProperties.CacheConfig(maxSize = 0)
        }

        shouldThrow<IllegalArgumentException> {
            RateLimitProperties.CacheConfig(ttlMinutes = 0)
        }
    }

    @Test
    fun `should correctly configure bandwidth limits`() {
        val limit = RateLimitProperties.BandwidthLimit(
            name = "test",
            capacity = 100,
            refillTokens = 10,
            refillDuration = Duration.ofMinutes(1),
        )

        limit.name shouldBe "test"
        limit.capacity shouldBe 100
        limit.refillTokens shouldBe 10
        limit.refillDuration shouldBe Duration.ofMinutes(1)
        limit.initialTokens shouldBe null
    }

    @Test
    fun `should support optional initialTokens in bandwidth limit`() {
        val limit = RateLimitProperties.BandwidthLimit(
            name = "with-initial",
            capacity = 50,
            refillTokens = 5,
            refillDuration = Duration.ofMinutes(1),
            initialTokens = 25L,
        )

        limit.initialTokens shouldBe 25L
    }

    @Test
    fun `should reject negative cache maxSize`() {
        shouldThrow<IllegalArgumentException> {
            RateLimitProperties.CacheConfig(maxSize = -1)
        }
    }

    @Test
    fun `should reject negative cache ttlMinutes`() {
        shouldThrow<IllegalArgumentException> {
            RateLimitProperties.CacheConfig(ttlMinutes = -1)
        }
    }

    @Test
    fun `should have correct default API key prefixes`() {
        val config = RateLimitProperties.ApiKeyPrefixConfig()

        config.professional shouldBe "PX001-"
        config.basic shouldBe "BX001-"
    }

    @Test
    fun `should have default auth endpoints configured`() {
        val properties = RateLimitProperties()

        properties.auth.limits.isNotEmpty() shouldBe true
        properties.auth.endpoints.isNotEmpty() shouldBe true
        properties.auth.endpoints.any { it.contains("/auth/") } shouldBe true
    }

    @Test
    fun `should have default business pricing plans`() {
        val properties = RateLimitProperties()

        properties.business.pricingPlans.containsKey(RateLimitProperties.TIER_FREE) shouldBe true
        properties.business.pricingPlans.containsKey(RateLimitProperties.TIER_BASIC) shouldBe true
        properties.business.pricingPlans.containsKey(RateLimitProperties.TIER_PROFESSIONAL) shouldBe true
    }

    @Test
    fun `should have default resume and waitlist endpoints`() {
        val properties = RateLimitProperties()

        properties.resume.endpoints.isNotEmpty() shouldBe true
        properties.waitlist.endpoints.isNotEmpty() shouldBe true
    }

    @Test
    fun `should accept minimum valid cache configuration`() {
        val config = RateLimitProperties.CacheConfig(maxSize = 1, ttlMinutes = 1)

        config.maxSize shouldBe 1
        config.ttlMinutes shouldBe 1
    }
}
