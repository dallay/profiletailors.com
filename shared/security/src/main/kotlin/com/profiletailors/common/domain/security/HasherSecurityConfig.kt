package com.profiletailors.common.domain.security

/**
 * Configuration contract for hasher security settings.
 *
 * Implemented by Spring @ConfigurationProperties to bind
 * `profiletailors.security.*` values.
 */
interface HasherSecurityConfig {
    /** Secret key used for IP-address HMAC hashing. */
    val ipHmacSecret: String

    /** Allow insecure (e.g., SHA-256) hashers in production. */
    val allowInsecureHasher: Boolean
}
