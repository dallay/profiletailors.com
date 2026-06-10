package com.profiletailors.common.domain.bus.event

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter

class EventMultiplexer<E : DomainEvent> : EventConsumer<E> {
    private val consumers = mutableListOf<Pair<EventFilter<E>, EventConsumer<E>>>()

    fun on(filter: EventFilter<E>, consumer: EventConsumer<E>) {
        consumers.add(filter to consumer)
    }

    override suspend fun consume(event: E) {
        consumers.asFlow()
            .filter { (filter, _) -> filter.filter(event) }
            .collect { (_, consumer) -> consumer.consume(event) }
    }
}
