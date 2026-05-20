package com.profiletailors.common.domain.error

abstract class BusinessRuleValidationException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
