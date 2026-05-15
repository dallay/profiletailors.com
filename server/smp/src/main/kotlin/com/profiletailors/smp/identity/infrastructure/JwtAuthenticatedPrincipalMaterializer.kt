package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.credentials.domain.ValidatedToken
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType

class JwtAuthenticatedPrincipalMaterializer(
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
) {
    suspend fun materialize(token: ValidatedToken): AuthenticatedPrincipal {
        val principalFacts = principalIdentityLookup.findBySubject(
            principalType = PrincipalType.USER,
            subject = token.subject,
            provider = token.issuer,
        )
        val displayIdentity = principalFacts?.displayIdentity
            ?: principalFacts?.username
            ?: token.claims["preferred_username"]
            ?: principalFacts?.email
            ?: token.claims["email"]
            ?: token.subject

        return AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = principalFacts?.principalId ?: token.subject,
                principalType = PrincipalType.USER,
                subject = token.subject,
                provider = principalFacts?.provider ?: token.issuer,
                displayIdentity = displayIdentity,
                authenticationMethod = "JWT_BEARER",
                issuedCredentialReference = token.tokenId,
                attributes = token.claims,
            ),
            credentialType = CredentialType.JWT,
        )
    }
}
