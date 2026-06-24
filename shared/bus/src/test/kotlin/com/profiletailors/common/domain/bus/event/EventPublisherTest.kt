package com.profiletailors.common.domain.bus.event

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class EventPublisherTest {

    @Test
    fun `should publish individual events`() = runTest {
        val publisher = TestPublisher()
        val event = TestDomainEvent()

        publisher.publish(event)

        assertThat(publisher.published).containsExactly(event)
    }

    @Test
    fun `should publish a list of events individually`() = runTest {
        val publisher = TestPublisher()
        val events = listOf(TestDomainEvent(1), TestDomainEvent(2))

        publisher.publish(events)

        assertThat(publisher.published).hasSize(2)
    }

    private class TestPublisher : EventPublisher<TestDomainEvent> {
        val published = mutableListOf<TestDomainEvent>()

        override suspend fun publish(event: TestDomainEvent) {
            published.add(event)
        }
    }

    private data class TestDomainEvent(
        private val version: Int = 1,
    ) : DomainEvent {
        override fun eventVersion(): Int = version
        override fun occurredOn(): LocalDateTime? = LocalDateTime.now()
    }
}
