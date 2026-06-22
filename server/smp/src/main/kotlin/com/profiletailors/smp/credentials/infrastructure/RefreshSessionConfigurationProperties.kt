package com.profiletailors.smp.credentials.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for refresh-session cookie and token lifecycle.
 *
 * These remain in infrastructure and are adapted to the application-layer
 * RefreshSessionProperties model via a bean bridge in IdentityBootstrapConfiguration.
 */
@ConfigurationProperties(prefix = "app.security.refresh-session")
class RefreshSessionConfigurationProperties(
    val cookieName: String = "pt_refresh",
    val cookiePath: String = "/api/auth",
    val sameSite: String = "Lax",
    val secure: Boolean = true,
    val ttlSeconds: Long = 604800,
)
