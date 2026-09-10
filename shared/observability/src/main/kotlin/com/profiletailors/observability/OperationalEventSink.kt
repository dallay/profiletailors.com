package com.profiletailors.observability

fun interface OperationalEventSink {
    fun emit(event: OperationalEvent)
}

object NoOpOperationalEventSink : OperationalEventSink {
    override fun emit(event: OperationalEvent) = Unit
}

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

@Deprecated("Use the structured emit overload with a stable event name and named attributes")
fun OperationalEventSink.trace(message: String, vararg arguments: Any?) = emitLegacy(Severity.TRACE, message, arguments)

@Deprecated("Use the structured emit overload with a stable event name and named attributes")
fun OperationalEventSink.debug(message: String, vararg arguments: Any?) = emitLegacy(Severity.DEBUG, message, arguments)

@Deprecated("Use the structured emit overload with a stable event name and named attributes")
fun OperationalEventSink.info(message: String, vararg arguments: Any?) = emitLegacy(Severity.INFO, message, arguments)

@Deprecated("Use the structured emit overload with a stable event name and named attributes")
fun OperationalEventSink.warn(message: String, vararg arguments: Any?) = emitLegacy(Severity.WARN, message, arguments)

@Deprecated("Use the structured emit overload with a stable event name and named attributes")
fun OperationalEventSink.error(message: String, vararg arguments: Any?) = emitLegacy(Severity.ERROR, message, arguments)

private fun OperationalEventSink.emitLegacy(severity: Severity, message: String, arguments: Array<out Any?>) {
    val cause = arguments.lastOrNull() as? Throwable
    val values = if (cause == null) arguments else arguments.dropLast(1).toTypedArray()
    emit(
        OperationalEvent(
            name = message.substringBefore(' '),
            severity = severity,
            message = message,
            attributes = values.mapIndexed { index, _ -> "argument.$index" to "[REDACTED]" }.toMap(),
            cause = cause,
        ),
    )
}
