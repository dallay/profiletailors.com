package com.profiletailors.smp.observability.infrastructure

import com.profiletailors.observability.OperationalEvent
import com.profiletailors.observability.OperationalEventSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class OperationalEventPipelineBehaviorTest {
    @Test
    fun `successful request emits start and completion without payload`() = runBlocking {
        val events = mutableListOf<OperationalEvent>()
        val behavior = OperationalEventPipelineBehavior(OperationalEventSink { events += it })

        assertEquals("response", behavior.handle(SensitiveRequest("secret")) { "response" })

        assertEquals(listOf("bus.request.started", "bus.request.completed"), events.map { it.name })
        assertEquals("SensitiveRequest", events.first().attributes["request"])
        assertEquals(false, events.any { it.attributes.values.any { value -> value == "secret" } })
    }

    @Test
    fun `failed request is rethrown and includes cause`() = runBlocking {
        val events = mutableListOf<OperationalEvent>()
        val behavior = OperationalEventPipelineBehavior(OperationalEventSink { events += it })
        val failure = IllegalStateException("failure")

        val thrown = assertFailsWith<IllegalStateException> {
            behavior.handle("request") { throw failure }
        }

        assertSame(failure, thrown)
        assertEquals("bus.request.failed", events.last().name)
        assertSame(failure, events.last().cause)
    }

    @Test
    fun `cancellation is rethrown`() = runBlocking {
        val behavior = OperationalEventPipelineBehavior(OperationalEventSink { })
        val cancellation = CancellationException("cancelled")

        assertFailsWith<CancellationException> {
            behavior.handle("request") { throw cancellation }
        }
    }

    private data class SensitiveRequest(val secret: String)
}
