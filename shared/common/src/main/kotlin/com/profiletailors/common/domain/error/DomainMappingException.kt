package com.profiletailors.common.domain.error

class DomainMappingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
