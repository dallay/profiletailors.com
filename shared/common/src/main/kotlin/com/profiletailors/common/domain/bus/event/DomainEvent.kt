package com.profiletailors.common.domain.bus.event

import java.time.LocalDateTime

/**
 * Marker interface for domain events in the DDD event-driven architecture.
 *
 * A domain event represents something meaningful that happened in the domain that other
 * parts of the system (within or across bounded contexts) should react to. Events are
 * recorded by entities via [BaseEntity.record] and published through the bus infrastructure.
 *
 * ## Versioning
 * [eventVersion] describes the schema version of this event. It is expected to be **monotonic**
 * (each new schema rev increases the version) so consumers can decide whether they can process
 * a given payload. It does NOT describe a sequence number in the event store.
 *
 * ## Timing
 * [occurredOn] returns the [LocalDateTime] when the event occurred. A `null` value means
 * the event was synthetically generated or the timestamp was not yet assigned — consumers
 * should handle this gracefully.
 *
 * @see BaseEntity.record
 * @since 1.0.0
 */
interface DomainEvent {
    fun eventVersion(): Int
    fun occurredOn(): LocalDateTime?
}
