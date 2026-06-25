package com.profiletailors.controllers

import java.net.URI
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.server.ServerWebExchange

/**
 * Creates an RFC 7807 [ProblemDetail] with ProfileTailors standard metadata.
 *
 * All exception advices should use this factory to produce consistent error
 * responses, including stable error categories, timestamps, message keys,
 * localized messages, optional trace IDs, and optional request instances.
 *
 * @param status HTTP status represented by the problem detail.
 * @param title Short, human-readable error title.
 * @param detail Detailed message; when null, [title] is used as fallback.
 * @param typeSuffix Suffix appended to the application error documentation URL.
 * @param errorCategory Stable category used by clients and observability tooling.
 * @param exchange Optional exchange used to include request trace/instance data.
 * @param messageKey Optional i18n message key.
 * @param localizedMessage Optional localized message resolved by the caller.
 * @param additionalProperties Additional RFC 7807 extension properties.
 * @param includeInstance Whether to set the request path as the problem instance.
 * @return A fully populated [ProblemDetail].
 * @since 1.0.0
 */
@Suppress("LongParameterList")
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
