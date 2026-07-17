package com.profiletailors.smp.leadcapture.infrastructure.configuration

import com.profiletailors.common.domain.Service
import com.profiletailors.ratelimit.application.RateLimitingService
import com.profiletailors.ratelimit.infrastructure.adapter.ApiKeyParser
import com.profiletailors.ratelimit.infrastructure.adapter.Bucket4jRateLimiter
import com.profiletailors.ratelimit.infrastructure.adapter.ReactiveRateLimitingAdapter
import com.profiletailors.ratelimit.infrastructure.adapter.SpringRateLimitEventPublisher
import com.profiletailors.ratelimit.infrastructure.config.RateLimitConfiguration
import com.profiletailors.ratelimit.infrastructure.filter.RateLimitingFilter
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import

@Configuration
@Import(RateLimitConfiguration::class)
@ComponentScan(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = [Service::class],
        ),
    ],
    basePackageClasses = [
        RateLimitingService::class,
        ApiKeyParser::class,
        Bucket4jRateLimiter::class,
        ReactiveRateLimitingAdapter::class,
        SpringRateLimitEventPublisher::class,
        RateLimitingFilter::class,
        RateLimitMetrics::class,
    ],
)
class WaitlistRateLimitConfiguration
