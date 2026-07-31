package com.profiletailors.ratelimit.infrastructure.config

import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import com.profiletailors.ratelimit.infrastructure.store.LocalCaffeineRateLimitStore
import com.profiletailors.ratelimit.infrastructure.store.RateLimitStore
import com.profiletailors.ratelimit.infrastructure.store.RedisBucket4jRateLimitStore
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(RateLimitConfiguration::class.java)

    /**
     * Creates a BucketConfigurationFactory bean that can be injected into other components.
     */
    @Bean
    fun bucketConfigurationFactory(properties: RateLimitProperties): BucketConfigurationFactory =
        BucketConfigurationFactory(properties)

    @Bean
    fun rateLimitStore(properties: RateLimitProperties, metrics: RateLimitMetrics): RateLimitStore {
        if (!properties.store.distributedEnabled || properties.store.type == RateLimitProperties.StoreType.LOCAL) {
            logger.info("Using local Caffeine rate-limit store")
            return LocalCaffeineRateLimitStore(properties, metrics)
        }

        return when (properties.store.type) {
            RateLimitProperties.StoreType.REDIS -> {
                RedisBucket4jRateLimitStore(properties)
            }

            RateLimitProperties.StoreType.HAZELCAST -> {
                logger.warn("Hazelcast rate-limit store is not implemented yet. Falling back to local Caffeine store")
                LocalCaffeineRateLimitStore(properties, metrics)
            }

            RateLimitProperties.StoreType.LOCAL -> {
                LocalCaffeineRateLimitStore(properties, metrics)
            }
        }
    }
}
