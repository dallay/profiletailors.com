package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType

fun permissiveMediaPrincipalContextProvider(): PrincipalContextProvider =
    PermissiveMediaPrincipalContextProviderImpl()

private class PermissiveMediaPrincipalContextProviderImpl : PrincipalContextProvider {
    private val dummy = PrincipalContext(
        principalId = "test-principal",
        principalType = PrincipalType.USER,
        subject = "local:test@example.com",
        provider = null,
        displayIdentity = "Test User",
        authenticationMethod = "TEST",
    )

    override suspend fun current(): PrincipalContext = dummy
}