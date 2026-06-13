package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.bus.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class BaseEntityTest {

    // ── Event recording ──────────────────────────────────────────────────────

    @Test
    fun `should record a domain event`() {
        val entity = TestEntity(id = "entity-1")
        val event = TestDomainEvent()

        entity.record(event)
        val events = entity.pullDomainEvents()

        assertThat(events)
            .singleElement()
            .isEqualTo(event)
    }

    @Test
    fun `should clear domain events after pull`() {
        val entity = TestEntity(id = "entity-1")
        entity.record(TestDomainEvent())

        entity.pullDomainEvents()
        val secondPull = entity.pullDomainEvents()

        assertThat(secondPull).isEmpty()
    }

    @Test
    fun `should record multiple events`() {
        val entity = TestEntity(id = "entity-1")
        entity.record(TestDomainEvent(version = 1))
        entity.record(TestDomainEvent(version = 2))
        entity.record(TestDomainEvent(version = 3))

        val events = entity.pullDomainEvents()

        assertThat(events).hasSize(3)
    }

    @Test
    fun `should return empty list when no events recorded`() {
        val entity = TestEntity(id = "entity-1")

        val events = entity.pullDomainEvents()

        assertThat(events).isEmpty()
    }

    @Test
    fun `should preserve events in order`() {
        val entity = TestEntity(id = "entity-1")
        val event1 = TestDomainEvent(version = 1)
        val event2 = TestDomainEvent(version = 2)
        val event3 = TestDomainEvent(version = 3)

        entity.record(event1)
        entity.record(event2)
        entity.record(event3)
        val events = entity.pullDomainEvents()

        assertThat(events).containsExactly(event1, event2, event3)
    }

    // ── Equality ─────────────────────────────────────────────────────────────

    @Test
    fun `should consider entities with same id as equal`() {
        val entity1 = TestEntity(id = "same-id")
        val entity2 = TestEntity(id = "same-id")

        assertThat(entity1).isEqualTo(entity2)
        assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode())
    }

    @Test
    fun `should consider entities with different id as not equal`() {
        val entity1 = TestEntity(id = "one")
        val entity2 = TestEntity(id = "two")

        assertThat(entity1).isNotEqualTo(entity2)
    }

    @Test
    fun `should include domain events in equals check`() {
        val entity1 = TestEntity(id = "same-id")
        val entity2 = TestEntity(id = "same-id")

        entity1.record(TestDomainEvent())

        assertThat(entity1).isNotEqualTo(entity2)
    }

    // ── Test doubles ─────────────────────────────────────────────────────────

    private class TestEntity(
        override val id: String
    ) : BaseEntity<String>()

    private data class TestDomainEvent(
        private val version: Int = 1,
        private val occurred: LocalDateTime? = LocalDateTime.now()
    ) : DomainEvent {
        override fun eventVersion(): Int = version
        override fun occurredOn(): LocalDateTime? = occurred
    }
}
