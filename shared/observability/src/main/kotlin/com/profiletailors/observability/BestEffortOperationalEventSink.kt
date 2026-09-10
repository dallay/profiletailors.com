package com.profiletailors.observability

import java.util.concurrent.CancellationException

class BestEffortOperationalEventSink(private val delegate: OperationalEventSink) : OperationalEventSink {
    override fun emit(event: OperationalEvent) {
        try {
            delegate.emit(OperationalEventSanitizer.sanitize(event))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }
}
