package com.profiletailors.smp.config.bridges

import com.profiletailors.smp.governance.application.PrincipalIdentity
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import org.springframework.stereotype.Component

/**
 * Adapter that implements the governance port by delegating to the identity lookup service.
 * Lives in config layer to avoid governance depending on identity at the module level.
 */
@Component
internal class DelegatingPrincipalIdentity(private val principalIdentityLookup: PrincipalIdentityLookup) :
    PrincipalIdentity {
    override suspend fun findEmailByPrincipalId(principalId: String): String? =
        principalIdentityLookup.findByPrincipalId(principalId)?.email
}
