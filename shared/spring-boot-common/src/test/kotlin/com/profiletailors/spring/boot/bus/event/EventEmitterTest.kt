package com.profiletailors.spring.boot.bus.event

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventConsumer
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class EventEmitterTest {

    private data class TestEvent(val id: String) : DomainEvent {
        override fun eventVersion() = 1
        override fun occurredOn() = LocalDateTime.now()
    }

    @Test
    fun `should deliver published event to subscriber`() = runBlocking {
        val emitter = EventEmitter<TestEvent>()
        val received = AtomicReference<TestEvent>()

        emitter.on(
            filter = { true },
            consumer = EventConsumer<TestEvent> { event ->
                received.set(event)
            },
        )

        val event = TestEvent("abc")
        emitter.publish(event)

        assertNotNull(received.get())
        assertEquals("abc", received.get().id)
    }

    @Test
    fun `should not deliver event when filter rejects it`() = runBlocking {
        val emitter = EventEmitter<TestEvent>()
        val received = AtomicReference<TestEvent>()

        emitter.on(
            filter = { false },
            consumer = EventConsumer<TestEvent> { event ->
                received.set(event)
            },
        )

        emitter.publish(TestEvent("should-not-reach"))

        assertNull(received.get())
    }

    @Test
    fun `should deliver to multiple subscribers`() = runBlocking {
        val emitter = EventEmitter<TestEvent>()
        val received1 = AtomicReference<TestEvent>()
        val received2 = AtomicReference<TestEvent>()

        emitter.on(filter = { true }, consumer = EventConsumer { event: TestEvent -> received1.set(event) })
        emitter.on(filter = { true }, consumer = EventConsumer { event: TestEvent -> received2.set(event) })

        val event = TestEvent("multi")
        emitter.publish(event)

        assertEquals("multi", received1.get()?.id)
        assertEquals("multi", received2.get()?.id)
    }

    @Test
    fun `should filter by event property`() = runBlocking {
        val emitter = EventEmitter<TestEvent>()
        val received = AtomicReference<TestEvent>()

        emitter.on(
            filter = { it.id == "target" },
            consumer = EventConsumer<TestEvent> { event ->
                received.set(event)
            },
        )

        emitter.publish(TestEvent("other"))
        assertNull(received.get())

        emitter.publish(TestEvent("target"))
        assertNotNull(received.get())
        assertEquals("target", received.get().id)
    }

    @Test
    fun `should publish list of events`() = runBlocking {
        val emitter = EventEmitter<TestEvent>()
        val received = mutableListOf<TestEvent>()

        emitter.on(filter = { true }, consumer = EventConsumer { event: TestEvent -> received.add(event) })

        emitter.publish(listOf(TestEvent("a"), TestEvent("b"), TestEvent("c")))

        assertEquals(3, received.size)
        assertEquals(listOf("a", "b", "c"), received.map { it.id })
    }
}
