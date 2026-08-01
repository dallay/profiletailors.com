package com.profiletailors.smp.mcp.adapter

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.ListProviderCatalogQuery

/**
 * MCP tool adapter for provider catalog read operations.
 *
 * Tool: `list_providers`
 * Returns provider availability metadata only — no credentials or secrets.
 */
class ProviderToolsAdapter(
    private val mediator: Mediator,
    private val errorMapper: McpErrorMapper,
) {

    suspend fun listProviders(): ToolResponse<Any> = runCatching {
        ToolResponse.success(mediator.send(ListProviderCatalogQuery) as Any)
    }.getOrElse { ex ->
        ToolResponse.failure(errorMapper.mapToError(ex))
    }
}
