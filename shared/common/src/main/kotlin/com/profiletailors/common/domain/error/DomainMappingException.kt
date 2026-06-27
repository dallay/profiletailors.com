package com.profiletailors.common.domain.error

/**
 * Exception thrown when a domain mapping operation fails.
 *
 * Use this when transforming data between layers (e.g., DTO to domain model,
 * database entity to domain model) and the source data cannot be mapped to a
 * valid domain object. This typically indicates data corruption or a version
 * mismatch between producers and consumers.
 *
 * @since 1.0.0
 */
class DomainMappingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
