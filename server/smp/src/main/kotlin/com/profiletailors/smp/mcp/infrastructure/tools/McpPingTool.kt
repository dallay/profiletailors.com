package com.profiletailors.smp.mcp.infrastructure.tools

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Internal health-check tool for MCP server.
 *
 * Returns the server's current time, the MCP feature flag status, and the
 * protocol version advertised in the handshake. Enabled when
 * `spring.ai.mcp.server.enabled=true`.
 */
@Component
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = ["enabled"],
    havingValue = "true",
)
class McpPingTool {

    @McpTool(
        name = "mcp_ping",
        description = "Health check — returns the current server time, feature flag status, " +
            "and protocol version.",
        generateOutputSchema = true,
    )
    fun ping(): McpPingResponse = McpPingResponse(
        now = Instant.now().toString(),
        featureFlag = "spring.ai.mcp.server.enabled=true",
        protocolVersion = PROTOCOL_VERSION,
    )

    data class McpPingResponse(val now: String, val featureFlag: String, val protocolVersion: String)

    companion object {
        private const val PROTOCOL_VERSION = "2025-03-26"
    }
}
