package com.profiletailors.smp.identity.application

interface IdentityRegistrationGateway {
    suspend fun createUserIdentity(
        principalId: String,
        subject: String,
        email: String,
        username: String,
        provider: String?,
        displayIdentity: String,
    )
}
