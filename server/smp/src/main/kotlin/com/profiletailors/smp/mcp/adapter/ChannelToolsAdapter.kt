package com.profiletailors.smp.mcp.adapter

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus

/**
 * MCP tool adapter for channel-related read operations.
 *
 * Tool: `list_channels`
 * The response DTO ([ConnectedSocialChannelSummary]) already excludes secrets
 * (providerAccessToken, refreshToken, OAuth secrets) — those live in the
 * credentials infrastructure layer and never surface via query handlers.
 */
class ChannelToolsAdapter(
    private val mediator: Mediator,
    private val errorMapper: McpErrorMapper,
) {

    suspend fun listChannels(status: String?): ToolResponse<Any> = runCatching {
        val connectionStatus = status?.let { SocialConnectionStatus.valueOf(it) }
        val query = ListConnectedChannelsQuery(status = connectionStatus)
        ToolResponse.success(mediator.send(query) as Any)
    }.getOrElse { ex ->
        ToolResponse.failure(errorMapper.mapToError(ex))
    }
}
