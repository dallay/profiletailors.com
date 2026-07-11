package com.profiletailors.smp.platform.infrastructure.bus

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.spring.boot.bus.event.EventEmitter
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
 */
@Primary
@Component
internal class SpringDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val eventEmitter: EventEmitter<DomainEvent>,
) : EventPublisher<DomainEvent> {
    override suspend fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
        eventEmitter.publish(event)
    }
}
