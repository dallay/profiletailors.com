package com.profiletailors.spring.boot.presentation

import com.profiletailors.common.domain.bus.query.Response
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Simple API response containing only a message and a server timestamp.
 *
 * Used for operations where only a status message is required, without any additional payload.
 *
 * @property message Human-readable outcome message for the operation (e.g., "Resource created successfully").
 * @property timestamp Server-side creation timestamp for this response, useful for tracing and debugging.
 */
data class MessageResponse(
    @field:Schema(
        description = "Operation outcome message",
        example = "Resource created successfully",
    )
    val message: String,
    @field:Schema(
        description = "Server timestamp",
        example = "2025-01-19T10:30:00Z",
    )
    val timestamp: Instant = Instant.now(),
) : Response
