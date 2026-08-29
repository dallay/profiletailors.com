package com.profiletailors.smp.mcp.tools

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

/**
 * MCP tool adapter for channel-related read operations.
 *
 * Tool: `list_channels`
 * The response DTO ([ConnectedSocialChannelSummary]) already excludes secrets
 * (providerAccessToken, refreshToken, OAuth secrets) — those live in the
 * credentials infrastructure layer and never surface via query handlers.
 */
@Component
class ChannelTools(private val mediator: Mediator, private val errorMapper: McpErrorMapper) {

    @McpTool(
        name = "list_channels",
        description = "List the social channels connected in the authenticated workspace.",
        generateOutputSchema = true,
    )
    suspend fun listChannels(
        @McpToolParam(
            description = "Optional connection status filter (ACTIVE, EXPIRED, REVOKED).",
            required = false,
        )
        status: String? = null,
    ): ToolResponse<Any> = runCatching {
        val connectionStatus = status?.let { SocialConnectionStatus.valueOf(it) }
        val query = ListConnectedChannelsQuery(status = connectionStatus)
        ToolResponse.success(mediator.send(query) as Any)
    }.getOrElse { ex ->
        ToolResponse.failure(errorMapper.mapToError(ex))
    }
}
