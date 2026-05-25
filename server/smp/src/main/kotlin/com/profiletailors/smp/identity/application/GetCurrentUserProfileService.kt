package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContextProvider

@Service
open class GetCurrentUserProfileService(
    private val principalContextProvider: PrincipalContextProvider,
    private val principalIdentityLookup: PrincipalIdentityLookup,
) {
    open suspend fun execute(): CurrentUserProfile {
        val principalContext = principalContextProvider.require()
        val identityFacts = principalIdentityLookup.findBySubject(
            principalType = principalContext.principalType,
            subject = principalContext.subject,
            provider = principalContext.provider,
        )

        return CurrentUserProfile(
            principalId = principalContext.principalId,
            email = identityFacts?.email ?: principalContext.attributes["email"],
            username = identityFacts?.username ?: principalContext.attributes["preferred_username"],
            displayIdentity = identityFacts?.displayIdentity
                ?: principalContext.displayIdentity
                ?: identityFacts?.username
                ?: identityFacts?.email
                ?: principalContext.subject,
        )
    }
}
