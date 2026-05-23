package com.profiletailors.smp.credentials.application

import java.time.Instant

enum class RefreshSessionStatus {
    ACTIVE,
    REVOKED,
    ROTATED,
    EXPIRED,
}

data class RefreshSessionToken(
    val lookupKey: String,
    val secret: String,
) {
    fun asCookieValue(): String = "$lookupKey.$secret"
}

data class CreatedRefreshSession(
    val id: String,
    val principalId: String,
    val refreshToken: RefreshSessionToken,
    val expiresAt: Instant,
)

data class ActiveRefreshSession(
    val id: String,
    val principalId: String,
    val lookupKey: String,
    val tokenVerifier: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)

data class RotatedRefreshSession(
    val previousSessionId: String,
    val current: CreatedRefreshSession,
)

enum class RefreshSessionFailureReason {
    MISSING,
    INVALID,
    REVOKED,
    ROTATED,
    EXPIRED,
}
