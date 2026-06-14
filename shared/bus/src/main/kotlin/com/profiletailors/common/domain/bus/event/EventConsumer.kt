package com.profiletailors.common.domain.bus.event

/**
 * Consumer of domain events published by an [EventPublisher].
 *
 * Mark a consumer implementation with [Subscribe] to register it for automatic discovery.
 *
 * @param E the domain event type this consumer handles
 */
fun interface EventConsumer<E : DomainEvent> {
    /** Process a published domain event. */
    suspend fun consume(event: E)
}
