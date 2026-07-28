package com.profiletailors.smp.identity.domain

import java.time.Instant
import java.util.UUID

data class PasswordResetToken(
    val id: UUID,
    val principalId: String,
    val tokenHash: String,
    val requestedAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
) {
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
    fun isUsed(): Boolean = usedAt != null
    fun isValid(now: Instant): Boolean = !isExpired(now) && !isUsed()
}
