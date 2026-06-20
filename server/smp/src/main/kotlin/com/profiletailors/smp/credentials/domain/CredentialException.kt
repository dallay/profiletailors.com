package com.profiletailors.smp.credentials.domain

open class CredentialException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
