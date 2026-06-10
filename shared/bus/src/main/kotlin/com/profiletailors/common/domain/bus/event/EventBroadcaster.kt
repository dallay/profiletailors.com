package com.profiletailors.common.domain.bus.event

class EventBroadcaster<E : DomainEvent> : EventPublisher<E> {
    private val eventPublishers = mutableListOf<EventPublisher<E>>()

    fun use(eventPublisher: EventPublisher<E>) {
        eventPublishers.add(eventPublisher)
    }

    override suspend fun publish(event: E) {
        eventPublishers.forEach { it.publish(event) }
    }
}
