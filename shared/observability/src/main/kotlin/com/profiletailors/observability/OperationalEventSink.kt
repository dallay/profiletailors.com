package com.profiletailors.observability

fun interface OperationalEventSink {
    fun emit(event: OperationalEvent)
}

val NoOpOperationalEventSink: OperationalEventSink = OperationalEventSink { }

fun OperationalEventSink.emit(
    severity: Severity,
    name: String,
    message: String? = null,
    cause: Throwable? = null,
    vararg attributes: Pair<String, Any?>,
) {
    emit(
        OperationalEvent(
            name = name,
            severity = severity,
            message = message,
            attributes = attributes.toMap(),
            cause = cause,
        ),
    )
}
