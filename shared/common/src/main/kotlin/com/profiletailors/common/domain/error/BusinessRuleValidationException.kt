package com.profiletailors.common.domain.error

/**
 * Abstract base for domain-level business rule violations.
 *
 * Throw this (or a subclass) when an operation fails because it would violate a
 * domain invariant — for example, attempting to create a user with a duplicate email,
 * or scheduling a post in the past.
 *
 * This is distinct from technical exceptions (network errors, database failures) and
 * from validation of primitive input format (which may use [IllegalArgumentException]).
 *
 * @since 1.0.0
 */
abstract class BusinessRuleValidationException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
