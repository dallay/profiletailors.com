package com.profiletailors.common.domain.error

/**
 * Exception thrown when a requested domain entity cannot be found.
 *
 * Use this in repositories and application services when an entity lookup by
 * identifier returns no result. Examples: user not found, workspace not found,
 * post not found.
 *
 * @since 1.0.0
 */
abstract class EntityNotFoundException(override val message: String, override val cause: Throwable? = null) :
    BusinessRuleValidationException(message, cause)
