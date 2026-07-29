package com.profiletailors.smp.identity.infrastructure.email

import kotlinx.coroutines.delay
import java.time.Duration

fun interface PasswordResetRetryDelay {
    suspend fun await(duration: Duration)
}

object CoroutinePasswordResetRetryDelay : PasswordResetRetryDelay {
    override suspend fun await(duration: Duration) {
        delay(duration.toMillis())
    }
}
