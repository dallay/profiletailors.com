package com.profiletailors.controllers

import java.net.URI
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.server.ServerWebExchange

/**
 * Options for [createProblemDetail] — groups all optional/problem-detail-specific fields
 * so the function signature stays under SonarQube's 7-parameter limit.
 */
data class ProblemDetailOptions(
    val exchange: ServerWebExchange? = null,
    val messageKey: String? = null,
    val localizedMessage: String? = null,
    val additionalProperties: Map<String, Any>? = null,
    val includeInstance: Boolean = false,
)

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
 * @param options Optional metadata — exchange, messageKey, localizedMessage, additionalProperties, includeInstance.
 * @return A fully populated [ProblemDetail].
 * @since 1.0.0
 */
fun createProblemDetail(
    status: HttpStatus,
    title: String,
    detail: String?,
    typeSuffix: String,
    errorCategory: String,
    options: ProblemDetailOptions = ProblemDetailOptions(),
): ProblemDetail {
    val problemDetail = ProblemDetail.forStatusAndDetail(status, detail ?: title)
    problemDetail.title = title
    problemDetail.type = URI.create("$ERROR_PAGE/$typeSuffix")
    problemDetail.setProperty(ERROR_CATEGORY, errorCategory)
    problemDetail.setProperty(TIMESTAMP, Instant.now())

    val exchange = options.exchange
    if (exchange != null) {
        problemDetail.setProperty(TRACE_ID, exchange.request.id)
        if (options.includeInstance) {
            problemDetail.instance = URI.create(exchange.request.path.toString())
        }
    }

    if (options.messageKey != null) {
        problemDetail.setProperty(MESSAGE_KEY, options.messageKey)
    }
    if (options.localizedMessage != null) {
        problemDetail.setProperty(LOCALIZED_MESSAGE, options.localizedMessage)
    }

    options.additionalProperties?.forEach { (key, value) ->
        problemDetail.setProperty(key, value)
    }

    return problemDetail
}
