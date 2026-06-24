package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.bus.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class AggregateRootTest {

    @Test
    fun `should create aggregate root with given id`() {
        val aggregate = TestAggregateRoot(id = "agg-1")

        assertThat(aggregate.id).isEqualTo("agg-1")
    }

    @Test
    fun `should record and pull domain events`() {
        val aggregate = TestAggregateRoot(id = "agg-1")
        val event = TestDomainEvent()

        aggregate.record(event)
        val events = aggregate.pullDomainEvents()

        assertThat(events).containsExactly(event)
    }

    @Test
    fun `should clear events after pull`() {
        val aggregate = TestAggregateRoot(id = "agg-1")
        aggregate.record(TestDomainEvent())

        aggregate.pullDomainEvents()
        val secondPull = aggregate.pullDomainEvents()

        assertThat(secondPull).isEmpty()
    }

    @Test
    fun `should inherit base entity identity equality`() {
        val agg1 = TestAggregateRoot(id = "same-id")
        val agg2 = TestAggregateRoot(id = "same-id")

        assertThat(agg1).isEqualTo(agg2)
        assertThat(agg1.hashCode()).isEqualTo(agg2.hashCode())
    }

    private class TestAggregateRoot(
        override val id: String,
    ) : AggregateRoot<String>()

    private data class TestDomainEvent(
        private val version: Int = 1,
    ) : DomainEvent {
        override fun eventVersion(): Int = version
        override fun occurredOn(): LocalDateTime? = LocalDateTime.now()
    }
}
