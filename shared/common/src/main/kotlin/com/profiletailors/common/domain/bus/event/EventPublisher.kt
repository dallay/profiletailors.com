package com.profiletailors.common.domain.bus.event

interface EventPublisher<E : DomainEvent> {
    suspend fun publish(event: E)
    suspend fun publish(events: List<E>) = events.forEach { publish(it) }
}
