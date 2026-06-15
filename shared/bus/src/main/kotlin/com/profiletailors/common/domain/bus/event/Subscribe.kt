package com.profiletailors.common.domain.bus.event

import kotlin.reflect.KClass

/**
 * Marks an [EventConsumer] implementation for automatic registration by the event bus.
 *
 * @param filterBy optional event type filter — only events of this type (or subtypes)
 *                 will be delivered to the consumer. Defaults to no filtering.
 */
annotation class Subscribe(
    val filterBy: KClass<*>
)
