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
            refillDuration = Duration.ofMinutes(1)
        )

        limit.name shouldBe "test"
        limit.capacity shouldBe 100
        limit.refillTokens shouldBe 10
        limit.refillDuration shouldBe Duration.ofMinutes(1)
        limit.initialTokens shouldBe null
    }
}
