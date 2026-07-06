package com.profiletailors.common.util

/**
 * Utility for reading system environment variables with safe defaults.
 */
object SystemEnvironment {
    private var envLookup: (String) -> String? = { System.getenv(it) }

    internal fun setLookup(lookup: (String) -> String?) {
        envLookup = lookup
    }

    internal fun resetLookup() {
        envLookup = { System.getenv(it) }
    }

    /**
     * Retrieves an environment variable value, falling back to a default.
     */
    fun getEnvOrDefault(key: String, default: String): String =
        envLookup(key).takeUnless { it.isNullOrBlank() } ?: default
}
