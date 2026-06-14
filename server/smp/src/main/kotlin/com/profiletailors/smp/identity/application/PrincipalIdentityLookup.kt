package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.EmailStatus

data class PrincipalIdentityFacts(
    val principalId: String,
    val principalType: PrincipalType,
    val subject: String,
    val provider: String?,
    val displayIdentity: String?,
    val email: String?,
    val username: String?,
    val emailStatus: EmailStatus? = null,
)

interface PrincipalIdentityLookup {
    suspend fun findBySubject(
        principalType: PrincipalType,
        subject: String,
        provider: String?,
    ): PrincipalIdentityFacts?

    suspend fun findByEmail(email: String): PrincipalIdentityFacts?

    suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts?
}

class NoOpPrincipalIdentityLookup : PrincipalIdentityLookup {
    override suspend fun findBySubject(
        principalType: PrincipalType,
        subject: String,
        provider: String?,
    ): PrincipalIdentityFacts? = null

    override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = null

    override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = null
}
