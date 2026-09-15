package com.profiletailors.smp.platformadmin.application.model

import java.time.Instant
import java.util.UUID

data class AdminDirectInvitationSummary(
    val invitationId: UUID,
    val email: String,
    val target: String,
    val workspaceId: String?,
    val status: String,
    val expiresAt: Instant,
    val version: Long,
)
