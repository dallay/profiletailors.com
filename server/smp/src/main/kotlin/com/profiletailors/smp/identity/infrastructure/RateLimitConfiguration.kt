package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.CloseAccountOrchestrationPort
import com.profiletailors.smp.identity.application.RateLimitPort
import com.profiletailors.smp.mcp.infrastructure.McpRateLimitFilter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateLimitConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun rateLimitPort(): RateLimitPort = InMemoryRateLimitAdapter()

    /**
     * MCP rate limiter with registered buckets:
     * - `mcp-channels-read`: 60 requests/minute/workspace
     * - `mcp-publications-read`: 30 requests/minute/workspace
     */
    @Bean
    fun mcpRateLimitFilter(): McpRateLimitFilter = McpRateLimitFilter()

    /**
     * Stub [CloseAccountOrchestrationPort] used until [com.profiletailors.smp.privacy.application.CloseAccountOrchestrator]
     * can be registered as a Spring bean. The real orchestrator depends on 5 sub-ports
     * that are not yet implemented in the infrastructure layer.
     */
    @Bean
    fun closeAccountOrchestrationPort(): CloseAccountOrchestrationPort = CloseAccountOrchestrationPort { principalId ->
        logger.warn(
            "CloseAccountOrchestrationPort.stub: orchestration not yet implemented for principal {}",
            principalId,
        )
    }
}
