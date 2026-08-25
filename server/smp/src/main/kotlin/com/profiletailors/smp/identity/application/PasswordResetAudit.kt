package com.profiletailors.smp.identity.application

import java.time.Instant

data class PasswordResetAuditEvent(val principalId: String, val occurredAt: Instant)

fun interface PasswordResetAudit {
    /**
     * Records an audit event for a completed password reset.
     *
     * @param event The completed password reset audit event to record.
     */
    suspend fun recordCompleted(event: PasswordResetAuditEvent)
}
