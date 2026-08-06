package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.SocialContentProviderException
import com.profiletailors.smp.publishing.domain.SocialContentProviderFailure

/** Retries only transient rate-limit failures and never retries authorization or provider errors. */
class SocialContentRetryPolicy(
    private val maxAttempts: Int = 3,
    private val backoff: suspend (attempt: Int) -> Unit = {},
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
}
