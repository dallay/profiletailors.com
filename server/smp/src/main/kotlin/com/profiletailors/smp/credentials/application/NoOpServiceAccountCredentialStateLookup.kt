package com.profiletailors.smp.credentials.application

import com.profiletailors.smp.credentials.application.ActiveServiceAccountCredential
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialFailureReason
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialStateLookup

internal class NoOpServiceAccountCredentialStateLookup : ServiceAccountCredentialStateLookup {
    override suspend fun requireActive(
        credentialReference: String,
        subject: String,
        provider: String,
    ): ActiveServiceAccountCredential = throw ServiceAccountCredentialNotActiveException(
        credentialReference = credentialReference,
        subject = subject,
        provider = provider,
        reason = ServiceAccountCredentialFailureReason.MISSING,
    )
}
