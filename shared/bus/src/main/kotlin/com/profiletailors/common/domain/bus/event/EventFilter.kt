package com.profiletailors.common.domain.bus.event

/**
 * Predicate for filtering domain events before they reach an [EventConsumer].
 *
 * Used by [EventMultiplexer] to route events to specific consumers based on
 * event properties.
 *
 * @param E the domain event type this filter evaluates
 */
fun interface EventFilter<E : DomainEvent> {
    /** Returns `true` if the event should be delivered, `false` to discard it. */
    suspend fun filter(event: E): Boolean
}
