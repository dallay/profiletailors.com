package com.profiletailors.smp.identity.application

fun interface InvitationRegistrationGateway {
    suspend fun acceptForRegistration(rawToken: String, email: String, principalId: String): String
}
