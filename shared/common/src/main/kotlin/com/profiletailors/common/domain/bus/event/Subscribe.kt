package com.profiletailors.common.domain.bus.event

import kotlin.reflect.KClass

annotation class Subscribe(
    val filterBy: KClass<*>
)
