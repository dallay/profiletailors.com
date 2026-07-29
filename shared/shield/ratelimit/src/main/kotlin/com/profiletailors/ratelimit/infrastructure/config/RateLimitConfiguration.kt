package com.profiletailors.ratelimit.infrastructure.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration class to enable rate limiting properties and create necessary beans.
 *
 * No nested `@ComponentScan` is declared: the custom
 * [com.profiletailors.common.domain.Service] marker is meta-annotated with
 * `@Component`, so Spring's default filter discovers application-layer handlers
 * automatically. Adding a nested `includeFilters` here would re-introduce the
 * restrictive-scan bug that this configuration previously had.
 *
 * @since 2.0.0
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitConfiguration {

    /**
     * Creates a BucketConfigurationFactory bean that can be injected into other components.
     */
    @Bean
    fun bucketConfigurationFactory(properties: RateLimitProperties): BucketConfigurationFactory =
        BucketConfigurationFactory(properties)
}
