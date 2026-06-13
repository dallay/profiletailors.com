package com.profiletailors.common.util

/**
 * Utility for reading system environment variables with safe defaults.
 *
 * Centralizes environment variable access so that call sites don't deal with
 * `null` checks or blank-string edge cases directly.
 *
 * @since 1.0.0
 */
object SystemEnvironment {
    /**
     * Retrieves an environment variable value, falling back to a default.
     *
     * Unlike a plain `System.getenv(key) ?: default`, this method also treats
     * blank values (empty or whitespace-only) as missing and returns the default.
     *
     * @param key the environment variable name
     * @param default the fallback value if the variable is unset or blank
     * @return the variable value, or [default]
     */
    fun getEnvOrDefault(key: String, default: String): String =
        System.getenv(key).takeUnless { it.isNullOrBlank() } ?: default
}
