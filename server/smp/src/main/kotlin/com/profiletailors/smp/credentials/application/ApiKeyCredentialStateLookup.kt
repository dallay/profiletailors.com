package com.profiletailors.smp.credentials.application

import com.profiletailors.common.domain.context.PrincipalType
import org.springframework.security.authentication.BadCredentialsException

data class ActiveApiKeyCredential(
    val principalId: String,
    val credentialReference: String,
    val subject: String,
    val provider: String?,
)

interface ApiKeyCredentialStateLookup {
    suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential
}

enum class ApiKeyCredentialFailureReason {
    MISSING,
    INVALID,
    INACTIVE,
    REVOKED,
    REPLACED,
}

class ApiKeyCredentialNotActiveException(
    val credentialReference: String,
    val principalId: String? = null,
    val principalType: PrincipalType = PrincipalType.API_KEY,
    val reason: ApiKeyCredentialFailureReason,
) : BadCredentialsException(
    when (reason) {
        ApiKeyCredentialFailureReason.MISSING -> "API key credential was not found."
        ApiKeyCredentialFailureReason.INVALID -> "API key credential is invalid."
        ApiKeyCredentialFailureReason.INACTIVE -> "API key credential is inactive."
        ApiKeyCredentialFailureReason.REVOKED -> "API key credential is revoked."
        ApiKeyCredentialFailureReason.REPLACED -> "API key credential has been replaced."
    },
)
