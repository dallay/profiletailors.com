package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.EmailStatus
import java.time.Instant

interface IdentityRegistrationGateway {
    suspend fun createUserIdentity(
        principalId: String,
        subject: String,
        email: String,
        username: String,
        provider: String?,
        displayIdentity: String,
        emailStatus: EmailStatus = EmailStatus.PENDING,
    )

    suspend fun createEmailVerificationToken(email: String, tokenHash: String, expiresAt: Instant)

    suspend fun verifyEmailToken(tokenHash: String): EmailVerificationTokenData?

    suspend fun markTokenUsed(tokenHash: String, now: Instant)

    suspend fun updateEmailStatus(email: String, emailStatus: EmailStatus)

    suspend fun invalidateEmailTokens(email: String)

    suspend fun findActiveTokenByEmail(email: String): EmailVerificationTokenData?
}

data class EmailVerificationTokenData(
    val email: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant?,
)
