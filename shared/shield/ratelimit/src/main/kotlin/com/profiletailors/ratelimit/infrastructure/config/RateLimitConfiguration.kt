package com.profiletailors.ratelimit.infrastructure.config

import com.profiletailors.common.domain.Service
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

/**
 * Configuration class to enable rate limiting properties and create necessary beans.
 *
 * @since 2.0.0
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
@ComponentScan(
    basePackages = ["com.profiletailors.ratelimit.application"],
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = [Service::class],
        ),
    ],
)
class RateLimitConfiguration {

    /**
     * Creates a BucketConfigurationFactory bean that can be injected into other components.
     */
    @Bean
    fun bucketConfigurationFactory(properties: RateLimitProperties): BucketConfigurationFactory =
        BucketConfigurationFactory(properties)
}
