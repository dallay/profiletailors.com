package com.profiletailors.common.domain.bus.event

import java.time.LocalDateTime

interface DomainEvent {
    fun eventVersion(): Int
    fun occurredOn(): LocalDateTime?
}
