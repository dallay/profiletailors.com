package com.profiletailors.smp.observability.infrastructure

import com.profiletailors.observability.OperationalEvent
import com.profiletailors.observability.OperationalEventSink
import com.profiletailors.observability.Severity
import org.slf4j.LoggerFactory

class Slf4jOperationalEventSink : OperationalEventSink {
    private val logger = LoggerFactory.getLogger("profiletailors.operational")

    override fun emit(event: OperationalEvent) {
        val message = format(event)
        when (event.severity) {
            Severity.TRACE -> logger.trace(message)
            Severity.DEBUG -> logger.debug(message)
            Severity.INFO -> logger.info(message)
            Severity.WARN -> logger.warn(message)
            Severity.ERROR -> logger.error(message)
        }
    }

    private fun format(event: OperationalEvent): String {
        val message = event.message?.let { render(it, event.attributes) } ?: event.name
        val prefix = if (message == event.name) message else "${event.name} $message"
        val namedAttributes = event.attributes.filterKeys { !it.startsWith(ARGUMENT_PREFIX) }
        if (namedAttributes.isEmpty()) return prefix
        val attributes = namedAttributes.entries.joinToString(" ") { (key, value) -> "$key=${value ?: "null"}" }
        return "$prefix $attributes"
    }

    private fun render(message: String, attributes: Map<String, Any?>): String {
        var rendered = message
        attributes.keys
            .filter { it.startsWith(ARGUMENT_PREFIX) }
            .sortedBy { it.removePrefix(ARGUMENT_PREFIX).toIntOrNull() }
            .forEach { key ->
                rendered = rendered.replaceFirst("{}", attributes[key]?.toString() ?: "null")
            }
        return rendered
    }

    private companion object {
        const val ARGUMENT_PREFIX = "argument."
    }
}
