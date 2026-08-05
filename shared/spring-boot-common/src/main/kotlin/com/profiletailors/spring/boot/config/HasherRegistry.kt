package com.profiletailors.spring.boot.config

import com.profiletailors.common.domain.security.Hasher
import com.profiletailors.common.infrastructure.security.HmacHasher
import com.profiletailors.common.infrastructure.security.Sha256Hasher
import org.springframework.stereotype.Component

/** Simple registry + factory for Hasher strategy implementations. */
@Component
class HasherRegistry(private val securityProperties: SecurityProperties) {
    init {
        // Fail fast: if configuration requests HMAC as default but no secret provided,
        // throw during bean construction so application context fails early.
        if (securityProperties.default == "hmac" && securityProperties.ipHmacSecret.isBlank()) {
            check(securityProperties.ipHmacSecret.isNotBlank()) {
                "HMAC selected as default hasher but application.hasher.ip-hmac-secret is blank"
            }
        }
    }

    private val available: Map<String, Hasher> by lazy {
        mapOf(
            "sha256" to Sha256Hasher(),
            "hmac" to HmacHasher(securityProperties.ipHmacSecret),
        )
    }

    fun get(name: String?): Hasher {
        val effective = name ?: securityProperties.default
        return available[effective] ?: available.getValue(securityProperties.default)
    }
}
