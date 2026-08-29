package com.profiletailors.smp.mcp.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class McpErrorMapperWriteCodesTest {

    private val mapper = McpErrorMapper()

    @Test
    fun `maps InsufficientScopeException to insufficient_scope error`() {
        val result = mapper.mapToError(McpInsufficientScopeException("mcp:publications:write"))

        assertThat(result.code).isEqualTo("insufficient_scope")
        assertThat(result.category).isEqualTo("authorization")
        assertThat(result.retryable).isFalse()
        assertThat(result.correlationId).isNotBlank()
    }

    @Test
    fun `maps PublicationNotFoundException to publication_not_found error`() {
        val result = mapper.mapToError(McpPublicationNotFoundException("pub-X"))

        assertThat(result.code).isEqualTo("publication_not_found")
        assertThat(result.category).isEqualTo("not_found")
        assertThat(result.retryable).isFalse()
        assertThat(result.correlationId).isNotBlank()
    }

    @Test
    fun `maps PublicationStateConflictException to publication_state_conflict error`() {
        val result = mapper.mapToError(McpPublicationStateConflictException("pub-Y", "PUBLISHED"))

        assertThat(result.code).isEqualTo("publication_state_conflict")
        assertThat(result.category).isEqualTo("validation")
        assertThat(result.retryable).isFalse()
        assertThat(result.correlationId).isNotBlank()
    }

    @Test
    fun `maps PublicationValidationFailedException to publication_validation_failed error`() {
        val result = mapper.mapToError(McpPublicationValidationFailedException("body required"))

        assertThat(result.code).isEqualTo("publication_validation_failed")
        assertThat(result.category).isEqualTo("validation")
        assertThat(result.retryable).isFalse()
        assertThat(result.correlationId).isNotBlank()
    }

    @Test
    fun `maps MediaUnavailableException to media_unavailable error`() {
        val result = mapper.mapToError(McpMediaUnavailableException("media-1"))

        assertThat(result.code).isEqualTo("media_unavailable")
        assertThat(result.category).isEqualTo("platform")
        assertThat(result.retryable).isTrue()
        assertThat(result.correlationId).isNotBlank()
    }

    @Test
    fun `maps IdempotencyConflictException to idempotency_conflict error`() {
        val result = mapper.mapToError(McpIdempotencyConflictException("create_publication", "ws-1"))

        assertThat(result.code).isEqualTo("idempotency_conflict")
        assertThat(result.category).isEqualTo("idempotency")
        assertThat(result.retryable).isFalse()
        assertThat(result.correlationId).isNotBlank()
    }
}
