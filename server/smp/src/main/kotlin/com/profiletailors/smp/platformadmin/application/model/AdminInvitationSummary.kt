package com.profiletailors.smp.platformadmin.application.model

import java.time.Instant
import java.util.UUID

data class AdminInvitationSummary(
    val id: UUID,
    val waitlistEntryId: String,
    val status: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
    val revokedBy: UUID?,
    val createdBy: UUID,
    val deliveryStatus: String,
    val deliveryAttemptCount: Int,
    val version: Long,
)
