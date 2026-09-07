package com.profiletailors.smp.observability.infrastructure

import com.profiletailors.observability.OperationalEvent
import com.profiletailors.observability.Severity
import org.junit.jupiter.api.Test

class Slf4jOperationalEventSinkTest {
    @Test
    fun `emits every severity and renders message attributes without throwing`() {
        val sink = Slf4jOperationalEventSink()

        Severity.entries.forEach { severity ->
            sink.emit(
                OperationalEvent(
                    name = "test.event",
                    severity = severity,
                    message = "workspace={} count={}",
                    attributes = mapOf("argument.0" to "ws-test", "argument.1" to 2, "source" to "unit"),
                    cause = IllegalStateException("test"),
                ),
            )
        }
        sink.emit(OperationalEvent(name = "test.event.without.message", severity = Severity.INFO))
        sink.emit(
            OperationalEvent(
                name = "test.event.with.nulls",
                severity = Severity.INFO,
                message = "value={}",
                attributes = mapOf("argument.0" to null, "nullable" to null),
            ),
        )
    }
}
