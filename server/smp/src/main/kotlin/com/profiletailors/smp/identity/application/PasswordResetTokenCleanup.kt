package com.profiletailors.smp.identity.application

import java.time.Instant

fun interface PasswordResetTokenCleanup {
    /**
     * Deletes password reset tokens that expired before the specified cutoff timestamp.
     *
     * @param cutoff The timestamp before which tokens are considered expired.
     * @return The number of deleted password reset tokens.
     */
    suspend fun deleteExpiredBefore(cutoff: Instant): Long
}
