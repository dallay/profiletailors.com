package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import org.springframework.security.authentication.AbstractAuthenticationToken

class AuthenticatedPrincipalAuthenticationToken(
    private val authenticatedPrincipal: AuthenticatedPrincipal,
    private val rawToken: String,
) : AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = rawToken

    override fun getPrincipal(): Any = authenticatedPrincipal

    override fun getName(): String = authenticatedPrincipal.context.subject
}
