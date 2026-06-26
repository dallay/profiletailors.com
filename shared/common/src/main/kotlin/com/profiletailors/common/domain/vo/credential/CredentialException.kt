package com.profiletailors.common.domain.vo.credential

import com.profiletailors.common.domain.error.BusinessRuleValidationException

/**
 * Exception thrown when credential validation fails.
 *
 * This includes password policy violations (too short, missing character categories)
 * and blank/empty credential values.
 *
 * @since 1.0.0
 */
class CredentialException(override val message: String, override val cause: Throwable? = null) :
    BusinessRuleValidationException(message, cause)
