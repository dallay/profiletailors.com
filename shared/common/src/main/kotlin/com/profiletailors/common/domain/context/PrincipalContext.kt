package com.profiletailors.common.domain.context

enum class PrincipalType {
    USER,
    SERVICE_ACCOUNT,
    API_KEY,
    SYSTEM,
    INTEGRATION,
    AGENT,
}

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
