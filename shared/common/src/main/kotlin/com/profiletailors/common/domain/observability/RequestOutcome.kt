package com.profiletailors.common.domain.observability

import com.profiletailors.common.domain.ValueObject

/**
 * Represents the outcome of an operation from an observability perspective.
 *
 * - [SUCCESS]: the operation completed as expected.
 * - [FAILURE]: the operation failed (business rule violation, system error, etc.).
 *
 * This is used for metrics and audit logging across bounded contexts.
 *
 * @since 1.0.0
 */
@ValueObject
enum class RequestOutcome {
    SUCCESS,
    FAILURE,
}
