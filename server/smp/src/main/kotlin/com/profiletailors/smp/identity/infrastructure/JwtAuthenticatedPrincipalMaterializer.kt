package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.application.NoOpServiceAccountCredentialStateLookup
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialFailureReason
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialStateLookup
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.credentials.domain.ValidatedToken
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.platform.application.MissingPrincipalContextException

class JwtAuthenticatedPrincipalMaterializer(
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val serviceAccountCredentialStateLookup:
        ServiceAccountCredentialStateLookup = NoOpServiceAccountCredentialStateLookup(),
) {
    suspend fun materialize(token: ValidatedToken): AuthenticatedPrincipal =
        if (token.principalTypeHint == PrincipalType.SERVICE_ACCOUNT) {
            materializeServiceAccount(token)
        } else {
            materializeUser(token)
        }

    private suspend fun materializeUser(token: ValidatedToken): AuthenticatedPrincipal {
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
                issuedCredentialReference = token.credentialReference,
                attributes = token.claims,
            ),
            credentialType = CredentialType.JWT,
        )
    }

    private suspend fun materializeServiceAccount(token: ValidatedToken): AuthenticatedPrincipal {
        val credentialReference = token.credentialReference
            ?: throw ServiceAccountCredentialNotActiveException(
                credentialReference = "missing",
                subject = token.subject,
                provider = token.issuer,
                reason = ServiceAccountCredentialFailureReason.MISSING,
            )
        val activeCredential = serviceAccountCredentialStateLookup.requireActive(
            credentialReference = credentialReference,
            subject = token.subject,
            provider = token.issuer,
        )
        val principalFacts = principalIdentityLookup.findBySubject(
            principalType = PrincipalType.SERVICE_ACCOUNT,
            subject = token.subject,
            provider = token.issuer,
        ) ?: throw MissingPrincipalContextException(
            "Authenticated service-account principal could not be materialized.",
        )

        return AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = principalFacts.principalId,
                principalType = PrincipalType.SERVICE_ACCOUNT,
                subject = token.subject,
                provider = principalFacts.provider ?: token.issuer,
                displayIdentity = principalFacts.displayIdentity ?: token.subject,
                authenticationMethod = "JWT_BEARER",
                issuedCredentialReference = activeCredential.credentialReference,
                attributes = token.claims,
            ),
            credentialType = CredentialType.SERVICE_ACCOUNT,
        )
    }
}
