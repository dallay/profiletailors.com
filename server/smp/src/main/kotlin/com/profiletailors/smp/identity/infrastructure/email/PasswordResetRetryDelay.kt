package com.profiletailors.smp.identity.infrastructure.email

import kotlinx.coroutines.delay
import java.time.Duration

fun interface PasswordResetRetryDelay {
    /**
     * Suspends until the specified duration has elapsed.
     *
     * @param duration The duration to wait.
     */
    suspend fun await(duration: Duration)
}

val coroutinePasswordResetRetryDelay: PasswordResetRetryDelay = PasswordResetRetryDelay { duration ->
    delay(duration.toMillis())
}
