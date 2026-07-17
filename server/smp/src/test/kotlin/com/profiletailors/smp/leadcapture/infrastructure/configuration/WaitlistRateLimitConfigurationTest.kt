package com.profiletailors.smp.leadcapture.infrastructure.configuration

import com.profiletailors.ratelimit.infrastructure.config.RateLimitConfiguration
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [RateLimitConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class WaitlistRateLimitConfigurationTest {

    @Autowired
    lateinit var properties: RateLimitProperties

    @Test
    fun `SMP binds only WAITLIST as an enabled shared rate limit strategy`() {
        assertThat(properties.waitlist.enabled).isTrue()
        assertThat(properties.waitlist.endpoints).containsExactly("/api/waitlists")
        assertThat(properties.auth.enabled).isFalse()
        assertThat(properties.business.enabled).isFalse()
        assertThat(properties.resume.enabled).isFalse()
    }
}
