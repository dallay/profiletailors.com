package com.profiletailors.common.domain.vo.credential

import com.profiletailors.common.domain.error.BusinessRuleValidationException

class CredentialException(
    override val message: String,
    override val cause: Throwable? = null
) : BusinessRuleValidationException(message, cause)
