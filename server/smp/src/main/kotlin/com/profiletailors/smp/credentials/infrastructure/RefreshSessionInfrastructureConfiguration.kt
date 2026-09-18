package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.RefreshSessionCookieFactory
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshTokenHasher
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers credentials infrastructure beans including [RefreshSessionConfigurationProperties]
 * and default secret verifier/hasher implementations.
 */
@Configuration
@EnableConfigurationProperties(RefreshSessionConfigurationProperties::class)
class RefreshSessionInfrastructureConfiguration {

    @Bean
    fun apiKeySecretVerifier(): ApiKeySecretVerifier = BCryptApiKeySecretVerifier()

    @Bean
    fun refreshTokenHasher(): RefreshTokenHasher = BCryptRefreshTokenHasher()

    @Bean
    fun refreshSessionProperties(config: RefreshSessionConfigurationProperties): RefreshSessionProperties =
        RefreshSessionProperties(
            cookieName = config.cookieName,
            cookiePath = config.cookiePath,
            sameSite = config.sameSite,
            secure = config.secure,
            ttlSeconds = config.ttlSeconds,
        )

    @Bean
    fun refreshSessionCookieFactory(properties: RefreshSessionProperties): RefreshSessionCookieFactory =
        RefreshSessionCookieFactory(properties)
}
