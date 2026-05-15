package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.PrincipalType

data class PrincipalIdentityFacts(
    val principalId: String,
    val principalType: PrincipalType,
    val subject: String,
    val provider: String?,
    val displayIdentity: String?,
    val email: String?,
    val username: String?,
)

interface PrincipalIdentityLookup {
    suspend fun findBySubject(
        principalType: PrincipalType,
        subject: String,
        provider: String?,
    ): PrincipalIdentityFacts?
}

class NoOpPrincipalIdentityLookup : PrincipalIdentityLookup {
    override suspend fun findBySubject(
        principalType: PrincipalType,
        subject: String,
        provider: String?,
    ): PrincipalIdentityFacts? = null
}
