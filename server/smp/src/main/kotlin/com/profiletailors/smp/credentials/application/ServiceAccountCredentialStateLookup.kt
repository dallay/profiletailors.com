package com.profiletailors.smp.credentials.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.credentials.domain.CredentialException

data class ActiveServiceAccountCredential(
    val principalId: String,
    val credentialReference: String,
)

interface ServiceAccountCredentialStateLookup {
    suspend fun requireActive(
        credentialReference: String,
        subject: String,
        provider: String,
    ): ActiveServiceAccountCredential
}

enum class ServiceAccountCredentialFailureReason {
    MISSING,
    REVOKED,
}

class ServiceAccountCredentialNotActiveException(
    val credentialReference: String,
    val subject: String,
    val provider: String,
    val principalId: String? = null,
    val principalType: PrincipalType = PrincipalType.SERVICE_ACCOUNT,
    val reason: ServiceAccountCredentialFailureReason,
) : CredentialException(
    when (reason) {
        ServiceAccountCredentialFailureReason.MISSING -> "Service account credential is not active."
        ServiceAccountCredentialFailureReason.REVOKED -> "Service account credential is revoked."
    },
)
