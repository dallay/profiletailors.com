package com.profiletailors.common.domain.context

interface PrincipalContextProvider {
    suspend fun current(): PrincipalContext?

    suspend fun require(): PrincipalContext =
        current() ?: throw MissingPrincipalContextException()
}

interface ResourceContextProvider {
    fun current(): ResourceContext?

    fun require(): ResourceContext =
        current() ?: throw MissingResourceContextException()
}

interface RequestPathProvider {
    fun current(): String?

    fun require(): String =
        current() ?: throw MissingRequestPathException()
}

class MissingPrincipalContextException(
    message: String = "Authenticated principal context is required.",
) : IllegalStateException(message)

class MissingResourceContextException(
    message: String = "Resolved resource context is required.",
) : IllegalStateException(message)

class MissingRequestPathException(
    message: String = "Resolved request path is required.",
) : IllegalStateException(message)
