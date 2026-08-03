package com.profiletailors.smp.mcp.infrastructure.tools

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Internal health-check tool for MCP server.
 *
 * Provides a simple ping/pong mechanism to verify the MCP transport is working.
 * Only enabled when `smp.mcp.internal-tools-enabled=true`.
 *
 * Stub implementation for PR 2. Real tool registration will come when
 * Spring AI MCP transport API is clarified.
 */
@Component
@ConditionalOnProperty(
    prefix = "smp.mcp",
    name = ["internal-tools-enabled"],
    havingValue = "true",
)
class McpPingTool {
    // Stub: tool registration will be implemented when MCP transport API is available
}
