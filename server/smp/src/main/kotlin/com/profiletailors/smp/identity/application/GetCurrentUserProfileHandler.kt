package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContextProvider

/**
 * Handler that retrieves the current authenticated user's profile.
 *
 * Resolves the authenticated principal from the security context and combines
 * it with persisted identity facts (email, username, display identity) to build
 * a complete [CurrentUserProfile].
 */
@Service
open class GetCurrentUserProfileHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val principalIdentityLookup: PrincipalIdentityLookup,
) {
    /**
     * Retrieves the current user's profile.
     *
     * @return The current user's profile combining principal context and identity facts.
     */
    open suspend fun handle(): CurrentUserProfile {
        val principalContext = principalContextProvider.require()
        val identityFacts = principalIdentityLookup.findByPrincipalId(principalContext.principalId)
            ?: principalIdentityLookup.findBySubject(
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
            emailStatus = identityFacts?.emailStatus,
        )
    }
}
