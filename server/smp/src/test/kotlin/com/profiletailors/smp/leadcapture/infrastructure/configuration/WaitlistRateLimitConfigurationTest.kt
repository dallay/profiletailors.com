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
class WaitlistRateLimitTestMeterConfig {
    @Bean
    fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
}

/**
 * Locks the production-safe default for SMP's shared WAITLIST rate-limit wiring.
 *
 * Production context (Kubernetes / Cloud Run ingress with multiple replicas) has two open
 * blocker that prevents `application.rate-limit.waitlist.enabled` from being on by default:
 *  - DALLAY-513 — without trusted-proxy wiring, `remoteAddress` is the ingress/load-balancer
 *    address instead of the real client.
 *
 * Until those are closed, the default MUST be `false` so flipping the limiter on is always
 * an explicit operator decision (env override `SMP_WAITLIST_RATE_LIMIT_ENABLED=true`).
 */
@SpringBootTest(
    classes = [RateLimitConfiguration::class, RateLimitMetrics::class, WaitlistRateLimitTestMeterConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class WaitlistRateLimitConfigurationTest {

    @Autowired
    lateinit var properties: RateLimitProperties

    @Test
    fun `SMP defaults shared WAITLIST to disabled so the limiter cannot be turned on by accident`() {
        assertThat(properties.waitlist.enabled).isFalse()
    }

    @Test
    fun `SMP keeps the existing non-WAITLIST shared rate-limit strategies disabled`() {
        assertThat(properties.auth.enabled).isFalse()
        assertThat(properties.business.enabled).isFalse()
        assertThat(properties.resume.enabled).isFalse()
    }

    @Test
    fun `SMP defaults to local store and keeps distributed backend opt-in`() {
        assertThat(properties.store.distributedEnabled).isFalse()
        assertThat(properties.store.type).isEqualTo(RateLimitProperties.StoreType.LOCAL)
    }

    @Test
    fun `SMP keeps the WAITLIST endpoint prefix and the documented 10 per minute bandwidth limit`() {
        assertThat(properties.waitlist.endpoints).containsExactly("/api/waitlists")
        assertThat(properties.waitlist.limit.name).isEqualTo("waitlist-per-minute")
        assertThat(properties.waitlist.limit.capacity).isEqualTo(10L)
        assertThat(properties.waitlist.limit.refillTokens).isEqualTo(10L)
    }
}
