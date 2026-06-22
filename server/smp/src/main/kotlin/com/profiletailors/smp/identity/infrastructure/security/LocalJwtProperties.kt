package com.profiletailors.smp.identity.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for local JWT signing (dev/test mode).
 *
 * These properties are consumed by [LocalJwtConfiguration] to build the JWT encoder
 * and the local-mode JWT decoder when no external identity provider is configured.
 */
@ConfigurationProperties(prefix = "app.security.local-jwt")
class LocalJwtProperties(
    /** Symmetric secret for HS256 signing. When blank a dev-only fallback is used. */
    val secret: String = "",
    /** JWT issuer claim value. */
    val issuer: String = "http://localhost/profiletailors-local",
    /** Access-token time-to-live in seconds. */
    val ttlSeconds: Long = 900,
)
