package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup
import com.profiletailors.smp.identity.infrastructure.ApiKeyAuthenticatedPrincipalMaterializer
import kotlinx.coroutines.reactor.mono
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import reactor.core.publisher.Mono

class ApiKeyPrincipalAuthenticationConverter(
    private val apiKeyCredentialStateLookup: ApiKeyCredentialStateLookup,
    private val principalMaterializer: ApiKeyAuthenticatedPrincipalMaterializer,
) : Converter<String, Mono<AbstractAuthenticationToken>> {
    override fun convert(source: String): Mono<AbstractAuthenticationToken> = mono {
        val activeCredential = apiKeyCredentialStateLookup.requireActive(source)
        val principal = principalMaterializer.materialize(activeCredential)
        AuthenticatedPrincipalAuthenticationToken(principal, source)
    }
}
