package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IdentityBootstrapConfiguration {

    @Bean
    fun jwtAuthenticatedPrincipalMaterializer(principalIdentityLookup: PrincipalIdentityLookup): JwtAuthenticatedPrincipalMaterializer =
        JwtAuthenticatedPrincipalMaterializer(principalIdentityLookup)
}
