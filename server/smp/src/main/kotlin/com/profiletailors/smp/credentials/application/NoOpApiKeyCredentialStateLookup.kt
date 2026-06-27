package com.profiletailors.smp.credentials.application

import com.profiletailors.smp.credentials.application.ActiveApiKeyCredential
import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup

internal class NoOpApiKeyCredentialStateLookup : ApiKeyCredentialStateLookup {
    override suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential =
        throw ApiKeyCredentialNotActiveException(
            credentialReference = "missing",
            reason = ApiKeyCredentialFailureReason.MISSING,
        )
}
