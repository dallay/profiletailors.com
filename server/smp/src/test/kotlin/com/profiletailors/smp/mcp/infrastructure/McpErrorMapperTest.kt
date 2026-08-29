package com.profiletailors.smp.mcp.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class McpErrorMapperTest {

    private val mapper = McpErrorMapper()

    @Test
    fun `maps IllegalArgumentException to publication_validation_failed`() {
        val result = mapper.mapToError(IllegalArgumentException("bad date range"))

        assertThat(result.code).isEqualTo("publication_validation_failed")
        assertThat(result.category).isEqualTo("validation")
        assertThat(result.retryable).isFalse()
        assertThat(result.message).doesNotContain("Exception")
        assertThat(result.correlationId).isNotBlank()
    }

    @Test
    fun `maps AccessDeniedException to forbidden error`() {
        val result = mapper.mapToError(
            org.springframework.security.access.AccessDeniedException("no access"),
        )

        assertThat(result.code).isEqualTo("forbidden")
        assertThat(result.category).isEqualTo("authorization")
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `maps unknown exception to internal error without leaking details`() {
        val result = mapper.mapToError(
            RuntimeException("SQL error: SELECT * FROM users WHERE id = 'x'; DROP TABLE users;"),
        )

        assertThat(result.code).isEqualTo("internal")
        assertThat(result.category).isEqualTo("internal")
        assertThat(result.retryable).isTrue()
        assertThat(result.message).doesNotContain("SQL")
        assertThat(result.message).doesNotContain("SELECT")
        assertThat(result.message).doesNotContain("DROP")
    }

    @Test
    fun `never exposes stack traces in error message`() {
        val ex = RuntimeException("something failed")
        ex.stackTrace = arrayOf(
            StackTraceElement("com.example.Foo", "bar", "Foo.kt", 42),
        )

        val result = mapper.mapToError(ex)

        assertThat(result.message).doesNotContain("com.example")
        assertThat(result.message).doesNotContain("Foo.kt")
        assertThat(result.message).doesNotContain("at ")
    }

    @Test
    fun `maps rate limit exception to rate_limit_exceeded`() {
        val result = mapper.mapToError(McpRateLimitExceededException("mcp-channels-read", 42))

        assertThat(result.code).isEqualTo("rate_limit_exceeded")
        assertThat(result.category).isEqualTo("throttling")
        assertThat(result.retryable).isTrue()
    }

    @Test
    fun `ApplicationError is serializable to JSON-like map`() {
        val result = mapper.mapToError(IllegalArgumentException("bad input"))

        val map = result.toMap()
        assertThat(map).containsKeys("code", "category", "message", "retryable", "correlationId")
    }
}
