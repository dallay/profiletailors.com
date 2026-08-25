package com.profiletailors.smp.mcp.infrastructure

import com.profiletailors.smp.mcp.tools.McpToolMetadata
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory rate limiter for MCP tool invocations.
 *
 * Buckets:
 * - `mcp-channels-read`: 60 requests/minute/workspace
 * - `mcp-publications-read`: 30 requests/minute/workspace
 *
 * Returns null if allowed, or an [ApplicationError] if rate-limited.
 */
class McpRateLimitFilter {

    private val counters = ConcurrentHashMap<String, WindowCounter>()

    private val bucketLimits: Map<String, Int> = mapOf(
        "mcp-channels-read" to 60,
        "mcp-publications-read" to 30,
    )

    /**
     * Checks whether the tool invocation is within the rate limit.
     *
     * @return null if allowed, [ApplicationError] if rate-limited.
     */
    fun checkRateLimit(toolName: String, workspaceId: String, now: Instant): ApplicationError? {
        val bucket = McpToolMetadata.rateLimitBucket(toolName) ?: return null
        val maxRequests = bucketLimits[bucket] ?: return null
        val key = "$bucket:$workspaceId"

        var wasBlocked = false
        counters.compute(key) { _, existing ->
            val windowStart = now.minus(WINDOW)
            if (existing == null || existing.windowStart.isBefore(windowStart)) {
                WindowCounter(now, 1)
            } else if (existing.count < maxRequests) {
                WindowCounter(existing.windowStart, existing.count + 1)
            } else {
                wasBlocked = true
                existing
            }
        }

        return if (wasBlocked) {
            ApplicationError(
                code = "rate_limit_exceeded",
                category = "throttling",
                message = "Too many requests. Please retry later.",
                retryable = true,
                correlationId = java.util.UUID.randomUUID().toString(),
            )
        } else {
            null
        }
    }

    private data class WindowCounter(val windowStart: Instant, val count: Int)

    companion object {
        private val WINDOW: Duration = Duration.ofMinutes(1)
    }
}
