package com.profiletailors.smp.bdd.glue

import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.test.web.reactive.server.EntityExchangeResult
import java.nio.charset.StandardCharsets

object RecurringPublishingState {
    var currentRecurringScheduleId: String? = null
    var latestPublishingResponse: EntityExchangeResult<ByteArray>? = null

    val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    fun reset() {
        currentRecurringScheduleId = null
        latestPublishingResponse = null
    }

    fun responseBodyText(): String =
        String(latestPublishingResponse?.responseBody ?: ByteArray(0), StandardCharsets.UTF_8)

    fun parseRecurringResponseStatus(): String {
        val body = responseBodyText()
        val map: Map<String, Any?> = objectMapper.readValue(body)
        val status = when (val schedules = map["schedules"]) {
            is List<*> -> schedules.firstOrNull()?.let { (it as? Map<*, *>)?.get("status") }
            else -> map["status"]
        }
        return requireNotNull(status as? String) { "Field 'status' is null in response: $body" }
    }

    inline fun <reified T> parseField(field: String): T {
        val body = responseBodyText()
        return try {
            val map: Map<String, Any?> = objectMapper.readValue(body)
            val raw = map[field]
            when {
                raw == null && null is T ->
                    @Suppress("UNCHECKED_CAST")
                    null
                        as T
                raw == null -> error("Field '$field' is null in response: $body")
                raw is T -> raw
                else -> error(
                    "Field '$field' has type ${raw::class.simpleName} " +
                        "but expected ${T::class.simpleName} in response: $body",
                )
            }
        } catch (e: Exception) {
            error("Failed to parse field '$field' from response: $body. Error: ${e.message}")
        }
    }
}
