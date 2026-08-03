package com.profiletailors.smp.mcp.infrastructure

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = ["enabled"],
    havingValue = "true",
)
internal class McpConfiguration {

    @Bean
    fun mcpRateLimitFilter(): McpRateLimitFilter = McpRateLimitFilter()
}
