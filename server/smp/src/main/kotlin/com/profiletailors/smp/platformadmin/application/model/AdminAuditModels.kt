package com.profiletailors.smp.platformadmin.application.model

import java.time.Instant
import java.util.UUID

data class AdminAuditEventSummary(
    val eventId: UUID,
    val occurredAt: Instant,
    val operatorPrincipalId: UUID,
    val operatorPlatformRoles: List<String>,
    val action: String,
    val targetType: String,
    val targetId: String,
    val result: String,
    val reason: String?,
    val correlationId: String?,
    val requestId: String?,
)

data class AdminOperatorSummary(
    val principalId: UUID,
    val email: String,
    val displayName: String?,
    val platformRoles: List<String>,
    val assignedAt: Instant,
)
