package com.profiletailors.spring.boot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "application.hasher")
data class SecurityProperties(
    val default: String = "sha256",
    val ipHmacSecret: String = "",
    val allowInsecureHasher: Boolean = false,
)
