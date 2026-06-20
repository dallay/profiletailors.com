package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.application.ActiveApiKeyCredential
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.MissingPrincipalContextException

open class ApiKeyAuthenticatedPrincipalMaterializer(
    private val principalIdentityLookup: PrincipalIdentityLookup,
) {
    open suspend fun materialize(activeCredential: ActiveApiKeyCredential): AuthenticatedPrincipal {
        val principalFacts = principalIdentityLookup.findBySubject(
            principalType = PrincipalType.API_KEY,
            subject = activeCredential.subject,
            provider = activeCredential.provider,
        ) ?: throw MissingPrincipalContextException("Authenticated API-key principal could not be materialized.")

        return AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = principalFacts.principalId,
                principalType = PrincipalType.API_KEY,
                subject = principalFacts.subject,
                provider = principalFacts.provider,
                displayIdentity = principalFacts.displayIdentity ?: principalFacts.subject,
                authenticationMethod = "API_KEY",
                issuedCredentialReference = activeCredential.credentialReference,
            ),
            credentialType = CredentialType.API_KEY,
            emailStatus = principalFacts.emailStatus,
        )
    }
}
