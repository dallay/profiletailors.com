package com.profiletailors.smp.observability.infrastructure

import com.profiletailors.smp.observability.application.ObservabilityHookRegistry
import com.profiletailors.smp.observability.domain.MetricsHook
import com.profiletailors.smp.observability.domain.RateLimitHook
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ObservabilityBootstrapConfiguration {

    @Bean
    @ConditionalOnMissingBean(MetricsHook::class)
    fun metricsHook(): MetricsHook = NoOpMetricsHook()

    @Bean
    @ConditionalOnMissingBean(RateLimitHook::class)
    fun rateLimitHook(): RateLimitHook = NoOpRateLimitHook()

    @Bean
    fun observabilityHookRegistry(
        metricsHook: MetricsHook,
        rateLimitHook: RateLimitHook,
    ): ObservabilityHookRegistry = ObservabilityHookRegistry(
        metricsHook = metricsHook,
        rateLimitHook = rateLimitHook,
    )
}
