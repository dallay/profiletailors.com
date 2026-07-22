package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts

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
