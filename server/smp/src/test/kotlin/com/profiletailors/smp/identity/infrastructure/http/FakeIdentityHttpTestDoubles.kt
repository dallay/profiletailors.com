package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts

internal class FakePrincipalContextProvider : PrincipalContextProvider {
    override suspend fun current(): PrincipalContext = PrincipalContext(
        principalId = "fake-user",
        principalType = PrincipalType.USER,
        subject = "local:fake@example.com",
    )
}

internal class FakePrincipalIdentityLookup : PrincipalIdentityLookup {
    override suspend fun findBySubject(
        principalType: PrincipalType,
        subject: String,
        provider: String?,
    ): PrincipalIdentityFacts? = null

    override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = null

    override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = null
}
