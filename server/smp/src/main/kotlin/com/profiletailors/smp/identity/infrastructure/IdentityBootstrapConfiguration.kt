package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup
import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.BCryptApiKeySecretVerifier
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialStateLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
}
