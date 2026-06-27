package com.profiletailors.common.domain.bus.event

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class EventBroadcasterTest {

    @Test
    fun `should publish to all registered publishers`() = runTest {
        val broadcaster = EventBroadcaster<TestDomainEvent>()
        val received1 = mutableListOf<TestDomainEvent>()
        val received2 = mutableListOf<TestDomainEvent>()

        broadcaster.use(TestPublisher { received1.add(it) })
        broadcaster.use(TestPublisher { received2.add(it) })

        val event = TestDomainEvent()
        broadcaster.publish(event)

        assertThat(received1).containsExactly(event)
        assertThat(received2).containsExactly(event)
    }

    @Test
    fun `should publish to no publishers when none registered`() = runTest {
        val broadcaster = EventBroadcaster<TestDomainEvent>()

        broadcaster.publish(TestDomainEvent())
        // No exception expected
    }

    @Test
    fun `should publish to only registered publishers`() = runTest {
        val broadcaster = EventBroadcaster<TestDomainEvent>()
        val received = mutableListOf<TestDomainEvent>()

        broadcaster.use(TestPublisher { received.add(it) })

        val event = TestDomainEvent()
        broadcaster.publish(event)
        broadcaster.publish(event)

        assertThat(received).hasSize(2)
        assertThat(received).allMatch { it == event }
    }

    private class TestPublisher(private val onPublish: (TestDomainEvent) -> Unit) : EventPublisher<TestDomainEvent> {
        override suspend fun publish(event: TestDomainEvent) {
            onPublish(event)
        }
    }

    private data class TestDomainEvent(private val version: Int = 1) : DomainEvent {
        override fun eventVersion(): Int = version
        override fun occurredOn(): LocalDateTime? = LocalDateTime.now()
    }
}
