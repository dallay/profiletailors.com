package com.profiletailors.smp.mcp.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

@Tag("fast")
class McpRateLimitFilterWriteBucketTest {

    @Test
    fun `mcp-publications-write bucket is configured with limit 15 per minute`() {
        val filter = McpRateLimitFilter()
        assertThat(filter.bucketLimit("mcp-publications-write")).isEqualTo(15)
    }

    @Test
    fun `blocks write requests after 15 per minute`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(15) { i ->
            val r = filter.checkRateLimitForBucket("mcp-publications-write", "ws-1", now.plusMillis(i.toLong()))
            assertThat(r).withFailMessage("Request $i should be allowed").isNull()
        }

        val blocked = filter.checkRateLimitForBucket("mcp-publications-write", "ws-1", now.plusMillis(16))
        assertThat(blocked).isNotNull
        assertThat(blocked!!.code).isEqualTo("rate_limit_exceeded")
        assertThat(blocked.category).isEqualTo("throttling")
        assertThat(blocked.retryable).isTrue()
    }

    @Test
    fun `different workspaces have independent write limits`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(15) { i ->
            filter.checkRateLimitForBucket("mcp-publications-write", "ws-1", now.plusMillis(i.toLong()))
        }

        val resultOtherWs = filter.checkRateLimitForBucket("mcp-publications-write", "ws-2", now.plusMillis(16))
        assertThat(resultOtherWs).isNull()
    }

    @Test
    fun `allows write requests again after window expires`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(15) { i ->
            filter.checkRateLimitForBucket("mcp-publications-write", "ws-1", now.plusMillis(i.toLong()))
        }

        val afterWindow = now.plus(Duration.ofMinutes(1)).plusSeconds(1)
        val result = filter.checkRateLimitForBucket("mcp-publications-write", "ws-1", afterWindow)
        assertThat(result).isNull()
    }

    @Test
    fun `read and write buckets are isolated per workspace`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(30) { i ->
            filter.checkRateLimitForBucket("mcp-publications-read", "ws-1", now.plusMillis(i.toLong()))
        }

        val writeResult = filter.checkRateLimitForBucket("mcp-publications-write", "ws-1", now.plusMillis(31))
        assertThat(writeResult).isNull()
    }

    @Test
    fun `unknown bucket returns null`() {
        val filter = McpRateLimitFilter()
        val result = filter.checkRateLimitForBucket("nonexistent-bucket", "ws-1", Instant.now())
        assertThat(result).isNull()
    }
}
