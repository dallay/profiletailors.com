package com.profiletailors.common.domain.context

/**
 * Holds the authenticated principal's identity for the current request.
 *
 * Populated by the authentication filter and accessible via [WorkspaceContextHolder][com.profiletailors.config.WorkspaceContextHolder].
 *
 * @param principalId unique identifier for the principal
 * @param principalType the type of principal (user, service, api-key)
 * @param subject the subject identifier (e.g., email, username)
 * @param provider the authentication provider (e.g., "jwt", "api-key")
 * @param displayIdentity human-readable display name
 * @param authenticationMethod how the principal was authenticated
 * @param issuedCredentialReference reference to the credential used
 * @param attributes additional attributes from the authentication source
 */
data class PrincipalContext(
    val principalId: String,
    val principalType: PrincipalType,
    val subject: String,
    val provider: String? = null,
    val displayIdentity: String? = null,
    val authenticationMethod: String? = null,
    val issuedCredentialReference: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)
