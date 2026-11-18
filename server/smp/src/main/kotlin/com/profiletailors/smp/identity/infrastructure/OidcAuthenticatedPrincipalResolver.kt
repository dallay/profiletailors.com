package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.authentication.CredentialType
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.ExternalIdentityResolutionService
import com.profiletailors.smp.identity.application.ValidatedExternalIdentity
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser

class OidcAuthenticatedPrincipalResolver(
    private val externalIdentityResolutionService: ExternalIdentityResolutionService,
    private val expectedIssuer: String,
) {
    suspend fun resolve(registrationId: String, oidcUser: OidcUser): AuthenticatedPrincipal? {
        val idToken = oidcUser.idToken
        val identity = validatedIdentity(registrationId, idToken)
        val resolved = externalIdentityResolutionService.resolve(identity) ?: return null
        val facts = resolved.principal
        return AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = facts.principalId,
                principalType = PrincipalType.USER,
                subject = identity.subject,
                provider = identity.issuer,
                displayIdentity = facts.displayIdentity ?: facts.username ?: facts.email ?: identity.subject,
                authenticationMethod = AUTHENTICATION_METHOD,
            ),
            credentialType = CredentialType.JWT,
            emailStatus = facts.emailStatus,
        )
    }

    fun validateAdminIdentity(idToken: OidcIdToken) {
        if (!hasRequiredAdminClaims(idToken)) throw invalidOidcIdentity()
    }

    private fun hasRequiredAdminClaims(idToken: OidcIdToken): Boolean {
        val validIssuerAndSubject = idToken.issuer?.toString() == expectedIssuer &&
            !idToken.subject.isNullOrBlank()
        val validClientIdentity = ADMIN_CLIENT_ID in idToken.audience &&
            idToken.getClaimAsString(AUTHORIZED_PARTY) == ADMIN_CLIENT_ID
        return validIssuerAndSubject && validClientIdentity && idToken.getClaimAsString(ACR) == ADMIN_ACR
    }

    private fun validatedIdentity(registrationId: String, idToken: OidcIdToken): ValidatedExternalIdentity {
        if (registrationId == ADMIN_CLIENT_ID) validateAdminIdentity(idToken)
        if (idToken.issuer?.toString() != expectedIssuer || idToken.subject.isNullOrBlank()) {
            throw invalidOidcIdentity()
        }
        if (idToken.audience.none { it == registrationId }) throw invalidOidcIdentity()

        return ValidatedExternalIdentity(
            issuer = expectedIssuer,
            subject = idToken.subject,
            email = idToken.getClaimAsString(EMAIL),
            emailVerified = idToken.getClaim<Boolean>(EMAIL_VERIFIED) == true,
        )
    }

    private fun invalidOidcIdentity() = OAuth2AuthenticationException(
        OAuth2Error(INVALID_OIDC_IDENTITY),
    )

    private companion object {
        const val AUTHENTICATION_METHOD = "OIDC_BFF"
        const val ADMIN_CLIENT_ID = "pt-admin"
        const val ADMIN_ACR = "2"
        const val AUTHORIZED_PARTY = "azp"
        const val ACR = "acr"
        const val EMAIL = "email"
        const val EMAIL_VERIFIED = "email_verified"
        const val INVALID_OIDC_IDENTITY = "invalid_oidc_identity"
    }
}
