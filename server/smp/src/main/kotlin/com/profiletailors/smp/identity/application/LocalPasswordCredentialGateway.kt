package com.profiletailors.smp.identity.application

interface LocalPasswordCredentialGateway {
    /**
 * Creates a local password credential for a principal.
 *
 * @param principalId The identifier of the principal associated with the credential.
 * @param passwordHash The hashed password to store.
 */
suspend fun create(principalId: String, passwordHash: String)
    /**
 * Finds a local password credential by email address.
 *
 * @param email The email address associated with the credential.
 * @return The matching credential record, or `null` if no credential is found.
 */
suspend fun findByEmail(email: String): LocalPasswordCredentialRecord?
}

data class LocalPasswordCredentialRecord(
    val principalId: String,
    val email: String,
    val username: String?,
    val passwordHash: String,
)
