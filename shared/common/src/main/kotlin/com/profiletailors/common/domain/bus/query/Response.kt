package com.profiletailors.common.domain.bus.query

/**
 * Marker interface for CQRS query responses.
 *
 * All query handlers return implementations of [Response]. [QueryResponse] wraps
 * a single typed value, making it the most common response type.
 *
 * @since 1.0.0
 */
interface Response

/**
 * Generic query response wrapping a single result value.
 *
 * @param T the type of the wrapped data
 * @since 1.0.0
 */
data class QueryResponse<T>(val data: T) : Response
