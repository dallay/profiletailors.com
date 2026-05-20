package com.profiletailors.spring.boot.config

import com.profiletailors.common.domain.security.HasherSecurityConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Adapter that implements the common HasherSecurityConfig port by reading from
 * [SecurityProperties] which is mapped from Spring Boot's properties.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties::class)
class HasherConfig(private val securityProperties: SecurityProperties) : HasherSecurityConfig {
    override val ipHmacSecret: String
        get() = securityProperties.ipHmacSecret

    override val allowInsecureHasher: Boolean
        get() = securityProperties.allowInsecureHasher
}
