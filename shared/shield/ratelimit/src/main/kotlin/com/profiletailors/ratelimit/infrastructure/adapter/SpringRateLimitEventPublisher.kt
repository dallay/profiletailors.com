package com.profiletailors.ratelimit.infrastructure.adapter

import com.cvix.common.domain.bus.event.EventPublisher
import com.profiletailors.ratelimit.domain.event.RateLimitExceededEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Spring adapter for publishing rate limit events.
 * Bridges the domain EventPublisher port to Spring's ApplicationEventPublisher.
 *
 * @property applicationEventPublisher Spring's event publisher
 */
@Component
class SpringRateLimitEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : EventPublisher<RateLimitExceededEvent> {

    private val logger = LoggerFactory.getLogger(SpringRateLimitEventPublisher::class.java)

    override suspend fun publish(event: RateLimitExceededEvent) {
        try {
            applicationEventPublisher.publishEvent(event)
            logger.debug("Published RateLimitExceededEvent for identifier: {}", event.identifier)
        } catch (e: IllegalArgumentException) {
            logger.error("Failed to publish RateLimitExceededEvent for identifier: {}", event.identifier, e)
            throw e
        } catch (e: IllegalStateException) {
            logger.error("Failed to publish RateLimitExceededEvent for identifier: {}", event.identifier, e)
            throw e
        }
    }
}
