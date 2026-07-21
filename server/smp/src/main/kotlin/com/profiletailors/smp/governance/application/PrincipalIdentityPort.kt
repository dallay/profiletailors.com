package com.profiletailors.smp.governance.application

/**
 * Port for governance to lookup principal identity information without directly
 * depending on the identity module, breaking the governance → identity cycle.
 */
interface PrincipalIdentityPort {
    suspend fun findEmailByPrincipalId(principalId: String): String?
}
