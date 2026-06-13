package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.Generated
import com.profiletailors.common.domain.bus.event.DomainEvent
import java.io.Serializable
import java.time.Instant

/**
 * Base class for all DDD entities in the domain model.
 *
 * An entity is an object with a continuous identity (the [id]) that goes through
 * different states over time. Two entities with the same [id] are considered equal
 * regardless of their attribute values.
 *
 * ## Identity
 * Each entity is identified by an [id] of type [ID]. The identity is established at
 * creation time and remains immutable for the entity's lifecycle. Equality and hashCode
 * are based solely on [id] and the recorded domain events.
 *
 * ## Domain Events
 * Entities can record domain events via [record] to capture significant domain occurrences.
 * Events are collected in-memory and retrieved (and cleared) by a single call to
 * [pullDomainEvents]. This enables an atomic "record-and-publish" pattern where an
 * application service fires all pending events after a successful transaction.
 *
 * ## Audit Fields
 * The base provides opt-in audit fields: [createdAt], [createdBy], [updatedAt], [updatedBy].
 * Subclasses override these with injected values; by default they indicate "system" origin.
 *
 * @param ID the type of the entity's identity
 * @since 1.0.0
 * @see AggregateRoot
 * @see AuditableEntity
 */
abstract class BaseEntity<ID> : Serializable {
    abstract val id: ID
    open val createdAt: Instant = Instant.now()
    open val createdBy: String = "system"
    open val updatedAt: Instant? = null
    open val updatedBy: String? = null
    private val domainEvents: MutableList<DomainEvent> = mutableListOf()

    /**
     * Records a domain event to be published later.
     *
     * The event is added to the internal collection and will be returned (and cleared)
     * when [pullDomainEvents] is called. This method does NOT publish the event immediately.
     *
     * @param event the domain event to record
     */
    fun record(event: DomainEvent) = domainEvents.add(event)

    /**
     * Returns all recorded domain events and clears the internal collection.
     *
     * Use this after the entity's state has been persisted to safely dispatch
     * all pending events. Subsequent calls return an empty list until new events
     * are recorded.
     *
     * @return an immutable snapshot of recorded events
     */
    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        clearDomainEvents()
        return events
    }

    private fun clearDomainEvents() = domainEvents.clear()

    @Generated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseEntity<*>) return false
        if (id != other.id) return false
        if (domainEvents != other.domainEvents) return false
        return true
    }

    @Generated
    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + domainEvents.hashCode()
        return result
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
