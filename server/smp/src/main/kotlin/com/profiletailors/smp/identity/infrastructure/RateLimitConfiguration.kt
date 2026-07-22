package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.RateLimitPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateLimitConfiguration {

    @Bean
    fun rateLimitPort(): RateLimitPort = InMemoryRateLimitAdapter()
}
