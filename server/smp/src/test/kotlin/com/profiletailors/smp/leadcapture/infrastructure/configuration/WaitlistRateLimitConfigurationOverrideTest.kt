package com.profiletailors.smp.leadcapture.infrastructure.configuration

import com.profiletailors.ratelimit.infrastructure.config.RateLimitConfiguration
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class WaitlistRateLimitOverrideTestMeterConfig {
    @Bean
    fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
}

/**
 * Locks the production-safe escape hatch for the WAITLIST rate-limit: an operator
 * (or the Postgres-backed integration suite) can flip the limiter on with
 * `application.rate-limit.waitlist.enabled=true`. The default-off test
 * (`WaitlistRateLimitConfigurationTest`) proves the safe default; this test proves the
 * override path still works once a deployment wants to enable it explicitly.
 */
@SpringBootTest(
    classes = [RateLimitConfiguration::class, RateLimitMetrics::class, WaitlistRateLimitOverrideTestMeterConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "application.rate-limit.waitlist.enabled=true",
    ],
)
class WaitlistRateLimitConfigurationOverrideTest {

    @Autowired
    lateinit var properties: RateLimitProperties

    @Test
    fun `setting application rate-limit waitlist enabled to true flips the bound property on`() {
        assertThat(properties.waitlist.enabled).isTrue()
        assertThat(properties.waitlist.endpoints).containsExactly("/api/waitlists")
        assertThat(properties.waitlist.limit.capacity).isEqualTo(10L)
        assertThat(properties.waitlist.limit.refillTokens).isEqualTo(10L)
    }
}
