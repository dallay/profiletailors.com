package com.profiletailors.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OperationalEventSinkTest {
    @Test
    fun `structured emission preserves severity attributes and cause`() {
        val events = mutableListOf<OperationalEvent>()
        val cause = IllegalStateException("failure")
        val sink = OperationalEventSink { events += it }

        sink.emit(
            severity = Severity.ERROR,
            name = "identity.reset.failed",
            message = "reset failed",
            cause = cause,
            "principalId" to "p-1",
        )

        val event = events.single()
        assertEquals("identity.reset.failed", event.name)
        assertEquals(Severity.ERROR, event.severity)
        assertEquals("reset failed", event.message)
        assertEquals("p-1", event.attributes["principalId"])
        assertSame(cause, event.cause)
    }

}
