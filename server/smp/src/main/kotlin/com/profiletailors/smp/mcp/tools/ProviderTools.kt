package com.profiletailors.smp.mcp.tools

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.ListProviderCatalogQuery
import kotlinx.coroutines.reactor.mono
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * MCP tool adapter for provider catalog read operations.
 *
 * Tool: `list_providers`
 * Returns provider availability metadata only — no credentials or secrets.
 */
@Component
class ProviderTools(private val mediator: Mediator, private val errorMapper: McpErrorMapper) {

    @McpTool(
        name = "list_providers",
        description = "List providers supported by the platform and their current availability " +
            "(with the workspace's quota and remaining connections).",

    )
    fun listProviders(): Mono<ToolResponse<Any>> = mono {
        runCatching {
            ToolResponse.success(mediator.send(ListProviderCatalogQuery) as Any)
        }.getOrElse { ex ->
            ToolResponse.failure(errorMapper.mapToError(ex))
        }
    }
}
