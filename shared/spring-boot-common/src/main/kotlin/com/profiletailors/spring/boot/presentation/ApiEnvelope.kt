package com.profiletailors.spring.boot.presentation

import com.profiletailors.common.domain.bus.query.Response
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Generic API response wrapper used across the application presentation layer.
 *
 * This data class standardizes HTTP responses by providing a human-readable
 * operation message, an optional payload of type [T], and a server timestamp
 * indicating when the response instance was created.
 *
 * Type parameter:
 * @param T the type of the response payload contained in [data]
 *
 * Properties:
 * @property message short, human-readable outcome message for the operation
 *   (for example: "Resource created successfully"). This should be suitable
 *   for logging and light client display.
 * @property data optional response payload. When present it contains the
 *   resource or DTO that the client expects. Use null when there is no
 *   additional payload to return.
 * @property timestamp the server-side creation timestamp for this response.
 *   Useful for tracing and debugging across distributed systems.
 *
 * This class implements [Response], the marker interface used by the
 * application's query/response bus to identify response types.
 */
open class ApiEnvelope<T>(
    @field:Schema(description = "Operation outcome message", example = "Resource created successfully")
    open val message: String,

    @field:Schema(description = "Response payload")
    open val data: T? = null,

    @field:Schema(description = "Server timestamp", example = "2025-01-19T10:30:00Z")
    val timestamp: Instant = Instant.now(),
) : Response
