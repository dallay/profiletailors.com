package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.infrastructure.OidcAuthenticatedPrincipalResolver
import kotlinx.coroutines.reactor.mono
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import reactor.core.publisher.Mono

class LocalPrincipalOidcUserService(
    private val principalResolver: OidcAuthenticatedPrincipalResolver,
    private val delegate: ReactiveOAuth2UserService<OidcUserRequest, OidcUser> = OidcReactiveOAuth2UserService(),
) : ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {
    override fun loadUser(userRequest: OidcUserRequest): Mono<OidcUser> = delegate.loadUser(userRequest)
        .flatMap { oidcUser ->
            mono {
                principalResolver.resolve(userRequest.clientRegistration.registrationId, oidcUser)
            }.map { principal -> ResolvedOidcUser(oidcUser, principal) }
                .switchIfEmpty(Mono.error(localPrincipalNotEligible()))
        }

    private fun localPrincipalNotEligible() = OAuth2AuthenticationException(
        OAuth2Error(LOCAL_PRINCIPAL_NOT_ELIGIBLE),
    )

    private companion object {
        const val LOCAL_PRINCIPAL_NOT_ELIGIBLE = "local_principal_not_eligible"
    }
}

class ResolvedOidcUser(private val delegate: OidcUser, val authenticatedPrincipal: AuthenticatedPrincipal) :
    OidcUser by delegate
