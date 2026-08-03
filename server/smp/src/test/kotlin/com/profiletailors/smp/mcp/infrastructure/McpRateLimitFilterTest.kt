package com.profiletailors.smp.mcp.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

@Tag("fast")
class McpRateLimitFilterTest {

    @Test
    fun `allows request within rate limit`() {
        val filter = McpRateLimitFilter()
        val result = filter.checkRateLimit("list_channels", "ws-1", Instant.now())

        assertThat(result).isNull()
    }

    @Test
    fun `blocks channels requests after 60 per minute`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(60) { i ->
            val r = filter.checkRateLimit("list_channels", "ws-1", now.plusMillis(i.toLong()))
            assertThat(r).withFailMessage("Request $i should be allowed").isNull()
        }

        val blocked = filter.checkRateLimit("list_channels", "ws-1", now.plusMillis(61))
        assertThat(blocked).isNotNull
        assertThat(blocked!!.code).isEqualTo("rate_limit_exceeded")
    }

    @Test
    fun `blocks publications requests after 30 per minute`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(30) { i ->
            val r = filter.checkRateLimit("list_publications", "ws-1", now.plusMillis(i.toLong()))
            assertThat(r).withFailMessage("Request $i should be allowed").isNull()
        }

        val blocked = filter.checkRateLimit("list_publications", "ws-1", now.plusMillis(31))
        assertThat(blocked).isNotNull
        assertThat(blocked!!.code).isEqualTo("rate_limit_exceeded")
    }

    @Test
    fun `different workspaces have independent limits`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(60) { i ->
            filter.checkRateLimit("list_channels", "ws-1", now.plusMillis(i.toLong()))
        }

        val resultOtherWs = filter.checkRateLimit("list_channels", "ws-2", now.plusMillis(61))
        assertThat(resultOtherWs).isNull()
    }

    @Test
    fun `unknown tool is not rate limited`() {
        val filter = McpRateLimitFilter()
        val result = filter.checkRateLimit("unknown_tool", "ws-1", Instant.now())

        assertThat(result).isNull()
    }

    @Test
    fun `allows requests again after window expires`() {
        val filter = McpRateLimitFilter()
        val now = Instant.now()

        repeat(60) { i ->
            filter.checkRateLimit("list_channels", "ws-1", now.plusMillis(i.toLong()))
        }

        val afterWindow = now.plus(Duration.ofMinutes(1)).plusSeconds(1)
        val result = filter.checkRateLimit("list_channels", "ws-1", afterWindow)
        assertThat(result).isNull()
    }
}
