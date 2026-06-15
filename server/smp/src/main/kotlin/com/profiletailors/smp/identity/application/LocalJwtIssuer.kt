package com.profiletailors.smp.identity.application

import java.time.Instant

interface LocalJwtIssuer {
    fun issue(
        principalId: String,
        subject: String,
        email: String,
        username: String?,
        issuedAt: Instant,
    ): IssuedAccessToken
}

data class IssuedAccessToken(
    val value: String,
    val expiresInSeconds: Long,
)
