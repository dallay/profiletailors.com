package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.Generated
import com.profiletailors.common.domain.bus.event.DomainEvent
import java.io.Serializable
import java.time.Instant

abstract class BaseEntity<ID> : Serializable {
    abstract val id: ID
    open val createdAt: Instant = Instant.now()
    open val createdBy: String = "system"
    open val updatedAt: Instant? = null
    open val updatedBy: String? = null
    private val domainEvents: MutableList<DomainEvent> = mutableListOf()

    fun record(event: DomainEvent) = domainEvents.add(event)

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
