package com.profiletailors.common.domain.bus.event

fun interface EventConsumer<E : DomainEvent> {
    suspend fun consume(event: E)
}
