package com.profiletailors.smp.mcp.adapter

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.ApplicationError
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.GetCalendarPublicationsQuery
import com.profiletailors.smp.publishing.application.ListPublicationsQuery
import com.profiletailors.smp.publishing.domain.PublicationStatus
import java.time.Instant

/**
 * Wrapper for tool results that carries either data or error.
 */
data class ToolResponse<T>(val isSuccess: Boolean, val data: T? = null, val error: ApplicationError? = null) {
    companion object {
        fun <T> success(data: T): ToolResponse<T> = ToolResponse(isSuccess = true, data = data)
        fun <T> failure(error: ApplicationError): ToolResponse<T> = ToolResponse(isSuccess = false, error = error)
    }
}

/**
 * MCP tool adapter for publication-related read operations.
 *
 * Tools: `list_publications`, `get_calendar`
 * Delegates to existing query handlers via the mediator.
 */
class PublicationToolsAdapter(private val mediator: Mediator, private val errorMapper: McpErrorMapper) {

    @Suppress("UnusedParameter")
    suspend fun listPublications(
        from: String,
        to: String,
        status: String?,
        channelId: String?,
        timezone: String?,
    ): ToolResponse<Any> = runCatching {
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)
        val pubStatus = status?.let { PublicationStatus.valueOf(it) }

        val query = ListPublicationsQuery(
            from = fromInstant,
            to = toInstant,
            status = pubStatus,
            socialAccountId = channelId,
        )
        ToolResponse.success(mediator.send(query) as Any)
    }.getOrElse { ex ->
        ToolResponse.failure(errorMapper.mapToError(ex))
    }

    suspend fun getCalendar(
        from: String,
        to: String,
        status: String?,
        channelId: String?,
        timezone: String?,
    ): ToolResponse<Any> = runCatching {
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)
        val pubStatus = status?.let { PublicationStatus.valueOf(it) }

        val query = GetCalendarPublicationsQuery(
            from = fromInstant,
            to = toInstant,
            status = pubStatus,
            socialAccountId = channelId,
            timezone = timezone ?: "UTC",
        )
        ToolResponse.success(mediator.send(query) as Any)
    }.getOrElse { ex ->
        ToolResponse.failure(errorMapper.mapToError(ex))
    }
}
