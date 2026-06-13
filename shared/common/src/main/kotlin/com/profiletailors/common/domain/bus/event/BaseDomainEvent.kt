package com.profiletailors.common.domain.bus.event

import com.profiletailors.common.domain.Generated
import java.time.LocalDateTime

/**
 * Convenience base class for domain events.
 *
 * Provides a default implementation of [DomainEvent] with:
 * - An auto-generated [occurredOn] timestamp set at construction time.
 * - A stable [eventVersion] (always `1`). Subclasses should override [eventVersion]
 *   when the event schema evolves.
 *
 * @since 1.0.0
 */
open class BaseDomainEvent(private val occuredOn: LocalDateTime = LocalDateTime.now()) : DomainEvent {
    private var eventVersion = 1

    @Generated
    override fun eventVersion(): Int = eventVersion

    @Generated
    override fun occurredOn(): LocalDateTime = occuredOn
}
