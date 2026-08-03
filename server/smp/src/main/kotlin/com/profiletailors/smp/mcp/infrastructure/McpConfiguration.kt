package com.profiletailors.smp.mcp.infrastructure

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * Entry point for the `mcp` bounded context's Spring configuration.
 *
 * In PR 1 this class is intentionally empty: it merely registers the package
 * with Spring's component scan and gates the whole context behind the
 * `SMP_MCP_ENABLED` feature flag. The actual bean wiring (security chain,
 * tool authorizer, error mapper, RFC 9728 metadata controller) lands in PR 2
 * and PR 3.
 *
 * **Why gate behind `SMP_MCP_ENABLED`?** Spring AI's auto-configuration already
 * creates the Streamable HTTP transport bean when `spring.ai.mcp.server.enabled=true`,
 * so this annotation only suppresses the placeholder `@Configuration` registration
 * — a defence against the bounded context leaking into environments that should
 * not see any MCP-related beans at all.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = ["enabled"],
    havingValue = "true",
)
internal class McpConfiguration
