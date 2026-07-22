package com.profiletailors.common.domain.bus.event

/**
 * Publishes domain events to registered consumers.
 *
 * Events are distributed to all [EventConsumer] instances that match the event type,
 * optionally filtered by [EventFilter] through an [EventMultiplexer].
 *
 * @param E the domain event type this publisher handles
 */
interface EventPublisher<E : DomainEvent> {
    /** Publish a single domain event. */
    suspend fun publish(event: E)

    /** Publish a batch of domain events. Each event is published individually. */
    suspend fun publish(events: List<E>) = events.forEach { publish(it) }

    companion object {
        fun <E : DomainEvent> noop(): EventPublisher<E> = object : EventPublisher<E> {
            override suspend fun publish(event: E) {}
        }
    }
}
