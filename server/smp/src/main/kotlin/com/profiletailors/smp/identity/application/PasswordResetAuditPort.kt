package com.profiletailors.smp.identity.application

import java.time.Instant

data class PasswordResetAuditEvent(val principalId: String, val occurredAt: Instant)

fun interface PasswordResetAuditPort {
    suspend fun recordCompleted(event: PasswordResetAuditEvent)
}
