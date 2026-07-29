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

object CoroutinePasswordResetRetryDelay : PasswordResetRetryDelay {
    /**
     * Suspends execution for the specified duration.
     *
     * @param duration The duration to wait.
     */
    override suspend fun await(duration: Duration) {
        delay(duration.toMillis())
    }
}
