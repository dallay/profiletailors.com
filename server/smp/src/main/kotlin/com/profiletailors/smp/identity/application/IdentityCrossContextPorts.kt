package com.profiletailors.smp.identity.application

data class IdentityRefreshSessionToken(val lookupKey: String, val secret: String) {
    fun asCookieValue(): String = "$lookupKey.$secret"
}

data class IdentityCreatedRefreshSession(val principalId: String, val refreshToken: IdentityRefreshSessionToken)

data class IdentityRotatedRefreshSession(val principalId: String, val refreshToken: IdentityRefreshSessionToken)

interface IdentityRefreshSessionPort {
    suspend fun issue(principalId: String): IdentityCreatedRefreshSession
    suspend fun rotate(rawRefreshToken: String): IdentityRotatedRefreshSession
    suspend fun revoke(rawRefreshToken: String)
}

class IdentityRefreshSessionNotActiveException : RuntimeException("Refresh session is not active.")

fun interface IdentityConsentRecorder {
    suspend fun recordContractAcceptance(
        workspaceId: String,
        principalId: String,
        purpose: String,
        policyVersion: String,
        source: String,
        locale: String,
    )
}
