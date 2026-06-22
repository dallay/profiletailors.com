package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup
import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionTokenService
import com.profiletailors.smp.credentials.application.RefreshTokenHasher
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialStateLookup
import com.profiletailors.smp.credentials.infrastructure.BCryptApiKeySecretVerifier
import com.profiletailors.smp.credentials.infrastructure.BCryptRefreshTokenHasher
import com.profiletailors.smp.credentials.infrastructure.RefreshSessionConfigurationProperties
import com.profiletailors.smp.credentials.infrastructure.RefreshSessionCookieFactory
import com.profiletailors.smp.identity.application.LocalJwtIssuer
import com.profiletailors.smp.identity.application.PasswordHasher
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.infrastructure.security.LocalJwtProperties
import com.profiletailors.smp.identity.infrastructure.security.NimbusLocalJwtIssuer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtEncoder
import java.time.Clock

@Configuration
class IdentityBootstrapConfiguration {

    @Bean
    fun jwtAuthenticatedPrincipalMaterializer(
        principalIdentityLookup: PrincipalIdentityLookup,
        serviceAccountCredentialStateLookup: ServiceAccountCredentialStateLookup,
    ): JwtAuthenticatedPrincipalMaterializer =
        JwtAuthenticatedPrincipalMaterializer(
            principalIdentityLookup = principalIdentityLookup,
            serviceAccountCredentialStateLookup = serviceAccountCredentialStateLookup,
        )

    @Bean
    fun apiKeyAuthenticatedPrincipalMaterializer(
        principalIdentityLookup: PrincipalIdentityLookup,
    ): ApiKeyAuthenticatedPrincipalMaterializer =
        ApiKeyAuthenticatedPrincipalMaterializer(principalIdentityLookup)

    @Bean
    fun apiKeySecretVerifier(): ApiKeySecretVerifier = BCryptApiKeySecretVerifier()

    @Bean
    fun passwordHasher(): PasswordHasher = BCryptPasswordHasher()

    @Bean
    fun refreshTokenHasher(): RefreshTokenHasher = BCryptRefreshTokenHasher()

    @Bean
    fun refreshSessionTokenService(): RefreshSessionTokenService = RefreshSessionTokenService()

    @Bean
    fun refreshSessionProperties(
        config: RefreshSessionConfigurationProperties,
    ): RefreshSessionProperties = RefreshSessionProperties(
        cookieName = config.cookieName,
        cookiePath = config.cookiePath,
        sameSite = config.sameSite,
        secure = config.secure,
        ttlSeconds = config.ttlSeconds,
    )

    @Bean
    fun refreshSessionCookieFactory(properties: RefreshSessionProperties): RefreshSessionCookieFactory =
        RefreshSessionCookieFactory(properties)

    @Bean
    fun refreshSessionLifecycleService(
        refreshSessionGateway: com.profiletailors.smp.credentials.application.RefreshSessionGateway,
        refreshSessionTokenService: RefreshSessionTokenService,
        refreshSessionProperties: RefreshSessionProperties,
        clock: Clock,
    ): RefreshSessionLifecycleService = RefreshSessionLifecycleService(
        refreshSessionGateway = refreshSessionGateway,
        refreshSessionTokenService = refreshSessionTokenService,
        properties = refreshSessionProperties,
        clock = clock,
    )

    @Bean
    fun localJwtIssuer(
        jwtEncoder: JwtEncoder,
        properties: LocalJwtProperties,
    ): LocalJwtIssuer = NimbusLocalJwtIssuer(jwtEncoder, properties.issuer, properties.ttlSeconds)

}
