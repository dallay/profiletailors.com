package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.EmailStatus
import java.time.Instant

fun interface LocalJwtIssuer {
    fun issue(
        principalId: String,
        subject: String,
        email: String,
        username: String?,
        emailStatus: EmailStatus,
        issuedAt: Instant,
    ): IssuedAccessToken
}

data class IssuedAccessToken(
    val value: String,
    val expiresInSeconds: Long,
)
