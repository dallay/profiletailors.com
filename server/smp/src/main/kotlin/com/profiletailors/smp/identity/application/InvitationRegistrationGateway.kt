package com.profiletailors.smp.identity.application

fun interface InvitationRegistrationGateway {
    /**
     * Accepts an invitation for user registration.
     *
     * @param rawToken The raw invitation token.
     * @param email The email address associated with the registration.
     * @param principalId The identifier of the registering principal.
     * @return The result of accepting the invitation.
     */
    suspend fun acceptForRegistration(rawToken: String, email: String, principalId: String): String
}
