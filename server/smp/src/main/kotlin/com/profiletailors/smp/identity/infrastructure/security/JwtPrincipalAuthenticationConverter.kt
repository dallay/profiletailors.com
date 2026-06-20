package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialNotActiveException
import com.profiletailors.smp.identity.infrastructure.JwtAuthenticatedPrincipalMaterializer
import kotlinx.coroutines.reactor.mono
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import reactor.core.publisher.Mono

class JwtPrincipalAuthenticationConverter(
    private val jwtValidatedTokenMapper: FederatedTokenValidator<Jwt>,
    private val principalMaterializer: JwtAuthenticatedPrincipalMaterializer,
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {
    override fun convert(source: Jwt): Mono<AbstractAuthenticationToken> = mono {
        val validatedToken = jwtValidatedTokenMapper.validate(source)
        try {
            val principal = principalMaterializer.materialize(validatedToken)
            AuthenticatedPrincipalAuthenticationToken(principal, source.tokenValue)
        } catch (exception: ServiceAccountCredentialNotActiveException) {
            throw BadCredentialsException(exception.message, exception)
        } catch (exception: MissingPrincipalContextException) {
            throw BadCredentialsException(exception.message, exception)
        }
    }
}
