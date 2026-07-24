package com.profiletailors.smp.credentials.infrastructure.security

import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.Jwt

@Configuration
class CredentialsSecurityConfiguration {
    @Bean
    fun jwtValidatedTokenMapper(): FederatedTokenValidator<Jwt> = SpringJwtValidatedTokenMapper()
}
