package com.profiletailors.notifications.application.ports

import com.profiletailors.notifications.domain.RenderedEmail

/**
 * Outbound port for dispatching a rendered email to a recipient.
 *
 * This is intentionally a thin port that maps directly to the [com.profiletailors.notifications.domain.RenderedEmail]
 * payload. The infrastructure layer adapts the existing identity `EmailSender` to this
 * interface so the notifications module does not depend on identity internals.
 */
fun interface EmailDispatcher {
    /**
     * Send a rendered email to [to]. Implementations return success or a descriptive
     * error; callers translate the result into a [com.profiletailors.notifications.domain.Notification]
     * status update.
     */
    suspend fun dispatch(to: String, email: RenderedEmail): EmailDispatchResult
}

/**
 * Outcome of an [EmailDispatcher.dispatch] call.
 */
sealed interface EmailDispatchResult {
    data object Success : EmailDispatchResult
    data class Failure(val error: String) : EmailDispatchResult
}
