package com.profiletailors.common.domain.bus.event

fun interface EventFilter<E : DomainEvent> {
    suspend fun filter(event: E): Boolean
}
