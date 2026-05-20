package com.profiletailors.controllers

import java.net.URI
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.server.ServerWebExchange

/**
 * Central factory for building ProblemDetail objects across advices.
 */
fun createProblemDetail(
    status: HttpStatus,
    title: String,
    detail: String?,
    typeSuffix: String,
    errorCategory: String,
    exchange: ServerWebExchange?,
    messageKey: String? = null,
    localizedMessage: String? = null,
    additionalProperties: Map<String, Any>? = null,
    includeInstance: Boolean = false,
): ProblemDetail {
    val problemDetail = ProblemDetail.forStatusAndDetail(status, detail ?: title)
    problemDetail.title = title
    problemDetail.type = URI.create("$ERROR_PAGE/$typeSuffix")
    problemDetail.setProperty(ERROR_CATEGORY, errorCategory)
    problemDetail.setProperty(TIMESTAMP, Instant.now())

    if (exchange != null) {
        problemDetail.setProperty(TRACE_ID, exchange.request.id)
        if (includeInstance) {
            problemDetail.instance = URI.create(exchange.request.path.toString())
        }
    }

    if (messageKey != null) {
        problemDetail.setProperty(MESSAGE_KEY, messageKey)
    }
    if (localizedMessage != null) {
        problemDetail.setProperty(LOCALIZED_MESSAGE, localizedMessage)
    }

    additionalProperties?.forEach { (key, value) ->
        problemDetail.setProperty(key, value)
    }

    return problemDetail
}
