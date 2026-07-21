package com.profiletailors.smp.config.adapters

import com.profiletailors.smp.governance.application.PrincipalIdentityPort
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import org.springframework.stereotype.Component

/**
 * Adapter that implements the governance port by delegating to the identity lookup service.
 * Lives in config layer to avoid governance depending on identity at the module level.
 */
@Component
internal class PrincipalIdentityAdapter(private val principalIdentityLookup: PrincipalIdentityLookup) :
    PrincipalIdentityPort {
    override suspend fun findEmailByPrincipalId(principalId: String): String? =
        principalIdentityLookup.findByPrincipalId(principalId)?.email
}
