package com.profiletailors.spring.boot.bus.event

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.EventFilter
import com.profiletailors.common.domain.bus.event.EventMultiplexer
import com.profiletailors.common.domain.bus.event.EventPublisher
import org.springframework.stereotype.Component

/**
 * Represents an event emitter that allows subscribing to events and publishing events of a specific type.
 *
 * @param E the type of events emitted by this event emitter.
 */
@Suppress("UNCHECKED_CAST")
@Component
class EventEmitter<E : DomainEvent> : EventPublisher<E> {
    private val eventMultiplexer = EventMultiplexer<E>()

    /**
     * Registers an event consumer with the given event filter.
     *
     * @param filter The event filter to apply.
     * @param consumer The event consumer to register.
     */
    fun on(filter: EventFilter<E>, consumer: EventConsumer<*>) {
        eventMultiplexer.on(filter, consumer as EventConsumer<E>)
    }

    /**
     * Publishes an event to the event multiplexer.
     *
     * @param event the event to be published
     */
    override suspend fun publish(event: E) {
        eventMultiplexer.consume(event)
    }
}
