package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure
import kotlinx.coroutines.delay

/** Retries only transient rate-limit failures and never retries authorization or provider errors. */
class SocialContentRetryPolicy(
    private val maxAttempts: Int = 3,
    private val backoff: suspend (attempt: Int) -> Unit = { attempt ->
        val shift = minOf(attempt - 1, MAX_BACKOFF_SHIFT)
        delay(minOf(DEFAULT_BACKOFF_MILLIS * (1L shl shift), MAX_BACKOFF_MILLIS))
    },
) {
    init {
        require(maxAttempts >= 1) { "Social content retry attempts must be at least 1." }
    }

    suspend fun <T> execute(operation: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return operation()
            } catch (exception: SocialContentProviderException) {
                if (exception.failure != SocialContentProviderFailure.RATE_LIMITED || attempt == maxAttempts) {
                    throw exception
                }
                backoff(attempt)
                attempt += 1
            }
        }
    }

    private companion object {
        const val DEFAULT_BACKOFF_MILLIS = 100L

        /** Largest backoff shift applied before the delay is capped by [MAX_BACKOFF_MILLIS]. */
        const val MAX_BACKOFF_SHIFT = 20

        /** Upper bound for a single retry delay, preventing Long overflow and unbounded waits. */
        const val MAX_BACKOFF_MILLIS = 60_000L
    }
}
