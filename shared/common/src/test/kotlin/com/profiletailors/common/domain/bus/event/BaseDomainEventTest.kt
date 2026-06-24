package com.profiletailors.common.domain.bus.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class BaseDomainEventTest {

    @Test
    fun `should have default event version of 1`() {
        val event = TestDomainEvent()

        assertThat(event.eventVersion()).isEqualTo(1)
    }

    @Test
    fun `should record occurredOn timestamp on creation`() {
        val before = LocalDateTime.now()
        val event = TestDomainEvent()
        val after = LocalDateTime.now()

        assertThat(event.occurredOn()).isNotNull
        assertThat(event.occurredOn()).isBetween(before, after)
    }

    @Test
    fun `should implement DomainEvent interface`() {
        val event = TestDomainEvent()

        assertThat(event).isInstanceOf(DomainEvent::class.java)
    }

    @Test
    fun `should use provided occurredOn when supplied`() {
        val fixedTime = LocalDateTime.of(2024, 6, 15, 10, 30, 0)
        val event = TestDomainEvent(occurredOn = fixedTime)

        assertThat(event.occurredOn()).isEqualTo(fixedTime)
    }

    private class TestDomainEvent(
        private val eventVersion: Int = 1,
        private val occurredOn: LocalDateTime = LocalDateTime.now(),
    ) : BaseDomainEvent(occurredOn) {
        override fun eventVersion(): Int = eventVersion
    }
}
