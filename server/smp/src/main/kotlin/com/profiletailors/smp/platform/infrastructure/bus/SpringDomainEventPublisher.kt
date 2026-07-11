package com.profiletailors.smp.platform.infrastructure.bus

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.spring.boot.bus.event.EventEmitter
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Primary [EventPublisher] for [DomainEvent]s.
 *
 * Fans events out to BOTH:
 *  - Spring's [ApplicationEventPublisher] — picked up by `@EventListener` methods
 *    such as [com.profiletailors.smp.audit.infrastructure.AuthorizationAuditEventListener].
 *  - The application [EventEmitter] — picked up by `@Subscribe`-annotated
 *    [com.profiletailors.common.domain.bus.event.EventConsumer] beans
 *    (e.g. `SendVerificationEmailConsumer`).
 *
 * Marking this bean `@Primary` means it satisfies every `EventPublisher<DomainEvent>`
 * injection point in the app. We must fan out to the legacy [EventEmitter] too,
 * otherwise `@Subscribe` consumers silently stop receiving events after this
 * publisher is registered.
 *
 * Each channel is invoked independently so a failure in one does not prevent
 * the other from receiving the event.
 */
@Primary
@Component
internal class SpringDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val eventEmitter: EventEmitter<DomainEvent>,
) : EventPublisher<DomainEvent> {

    override suspend fun publish(event: DomainEvent) {
        publishViaSpring(event)
        publishViaEventEmitter(event)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun publishViaSpring(event: DomainEvent) {
        try {
            applicationEventPublisher.publishEvent(event)
        } catch (e: Exception) {
            log.warn("Failed to publish {} via ApplicationEventPublisher", event::class.simpleName, e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun publishViaEventEmitter(event: DomainEvent) {
        try {
            eventEmitter.publish(event)
        } catch (e: Exception) {
            log.warn("Failed to publish {} via EventEmitter", event::class.simpleName, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SpringDomainEventPublisher::class.java)
    }
}
