package com.profiletailors.common.domain.bus.event

import kotlin.reflect.KClass

class TypeMatchEventFilter<T : DomainEvent>(private val clazz: KClass<T>) : EventFilter<T> {
    override suspend fun filter(event: T): Boolean = clazz.isInstance(event)
}
