package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.error.BusinessRuleValidationException

data class InvalidCursor(override val message: String, override val cause: Throwable? = null) :
    BusinessRuleValidationException(message, cause)
