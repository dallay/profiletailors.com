package com.profiletailors.smp.identity.application

interface LocalPasswordCredentialGateway {
    /**
 * Stores a password hash for a principal.
 *
 * @param principalId The identifier of the principal.
 * @param passwordHash The password hash to store.
 */
suspend fun create(principalId: String, passwordHash: String)
    /**
 * Retrieves the local-password credential record associated with an email address.
 *
 * @param email The email address associated with the credentials.
 * @return The matching credential record, or `null` if no record is found.
 */
suspend fun findByEmail(email: String): LocalPasswordCredentialRecord?
    /**
 * Retrieves the local-password credential record for a principal.
 *
 * @param principalId The principal identifier.
 * @return The matching credential record, or `null` if none exists.
 */
suspend fun findByPrincipalId(principalId: String): LocalPasswordCredentialRecord?
    /**
 * Replaces the password hash for a principal.
 *
 * @param principalId The identifier of the principal whose password hash is updated.
 * @param passwordHash The replacement password hash.
 */
suspend fun updatePasswordHash(principalId: String, passwordHash: String)
}

data class LocalPasswordCredentialRecord(
    val principalId: String,
    val email: String,
    val username: String?,
    val passwordHash: String,
)
