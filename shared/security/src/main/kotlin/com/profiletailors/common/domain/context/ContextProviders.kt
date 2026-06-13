package com.profiletailors.common.domain.context

/**
 * Provider for accessing the current request's [PrincipalContext].
 *
 * Implementations resolve the principal from the reactive context
 * (e.g., populated by a Spring WebFilter).
 */
interface PrincipalContextProvider {
    /** Return the current principal, or null if not authenticated. */
    suspend fun current(): PrincipalContext?

    /** Return the current principal, or throw if not authenticated. */
    suspend fun require(): PrincipalContext =
        current() ?: throw MissingPrincipalContextException()
}

/**
 * Provider for accessing the current request's [ResourceContext].
 */
interface ResourceContextProvider {
    /** Return the current resource context, or null if not available. */
    fun current(): ResourceContext?

    /** Return the current resource context, or throw if not available. */
    fun require(): ResourceContext =
        current() ?: throw MissingResourceContextException()
}

/**
 * Provider for accessing the current request path.
 */
interface RequestPathProvider {
    /** Return the current request path, or null if not available. */
    fun current(): String?

    /** Return the current request path, or throw if not available. */
    fun require(): String =
        current() ?: throw MissingRequestPathException()
}

/** Thrown when [PrincipalContext] is required but not available. */
class MissingPrincipalContextException(
    message: String = "Authenticated principal context is required.",
) : IllegalStateException(message)

/** Thrown when [ResourceContext] is required but not available. */
class MissingResourceContextException(
    message: String = "Resolved resource context is required.",
) : IllegalStateException(message)

/** Thrown when the request path is required but not available. */
class MissingRequestPathException(
    message: String = "Resolved request path is required.",
) : IllegalStateException(message)
