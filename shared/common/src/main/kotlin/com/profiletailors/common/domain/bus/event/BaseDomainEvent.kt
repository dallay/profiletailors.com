package com.profiletailors.common.domain.bus.event

import com.profiletailors.common.domain.Generated
import java.time.LocalDateTime

open class BaseDomainEvent(private val occuredOn: LocalDateTime = LocalDateTime.now()) : DomainEvent {
    private var eventVersion = 1

    @Generated
    override fun eventVersion(): Int = eventVersion

    @Generated
    override fun occurredOn(): LocalDateTime = occuredOn
}
