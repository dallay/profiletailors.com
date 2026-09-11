package com.profiletailors.smp.identity.application

interface InvitationRegistrationGateway {
    suspend fun prepare(rawToken: String, normalizedEmail: String): InvitationRegistrationContext

    suspend fun complete(
        context: InvitationRegistrationContext,
        rawToken: String,
        principalId: String,
        displayName: String,
    ): InvitationRegistrationResult
}
