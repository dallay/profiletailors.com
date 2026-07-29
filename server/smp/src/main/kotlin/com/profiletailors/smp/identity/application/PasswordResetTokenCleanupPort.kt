package com.profiletailors.smp.identity.application

import java.time.Instant

fun interface PasswordResetTokenCleanupPort {
    suspend fun deleteExpiredBefore(cutoff: Instant): Long
}
