package com.profiletailors.smp.credentials.application

import com.profiletailors.smp.identity.domain.PrincipalType
import org.springframework.security.authentication.BadCredentialsException

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

class NoOpServiceAccountCredentialStateLookup : ServiceAccountCredentialStateLookup {
    override suspend fun requireActive(
        credentialReference: String,
        subject: String,
        provider: String,
    ): ActiveServiceAccountCredential =
        throw ServiceAccountCredentialNotActiveException(
            credentialReference = credentialReference,
            subject = subject,
            provider = provider,
            reason = ServiceAccountCredentialFailureReason.MISSING,
        )
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
) : BadCredentialsException(
    when (reason) {
        ServiceAccountCredentialFailureReason.MISSING -> "Service account credential is not active."
        ServiceAccountCredentialFailureReason.REVOKED -> "Service account credential is revoked."
    },
)
