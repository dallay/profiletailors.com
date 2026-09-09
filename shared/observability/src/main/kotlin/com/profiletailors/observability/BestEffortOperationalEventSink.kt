package com.profiletailors.observability

import java.util.concurrent.CancellationException

/**
 * Protects business execution from failures in an operational-event adapter.
 *
 * Cancellation remains observable to coroutine callers, while ordinary adapter exceptions are
 * intentionally discarded. JVM [Error] types are not caught so fatal process conditions retain
 * their normal semantics.
 */
class BestEffortOperationalEventSink(private val delegate: OperationalEventSink) : OperationalEventSink {
    override fun emit(event: OperationalEvent) {
        try {
            delegate.emit(OperationalEventSanitizer.sanitize(event))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Operational telemetry is best effort and must not change business behavior.
        }
    }
}
