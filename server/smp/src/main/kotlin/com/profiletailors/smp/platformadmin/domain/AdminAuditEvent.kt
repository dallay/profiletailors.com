package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject
import java.time.Instant
import java.util.UUID

@ValueObject
enum class AdminAuditAction {
    PLATFORM_ROLE_ASSIGNED,
    PLATFORM_ROLE_REVOKED,
    WAITLIST_ENTRY_INVITED,
    WAITLIST_ENTRY_CANCELLED,
    INVITATION_RESENT,
    INVITATION_REVOKED,
    ADMIN_USER_VIEWED,
    ADMIN_WAITLIST_ENTRY_VIEWED,
}

@ValueObject
enum class AdminAuditResult {
    SUCCEEDED,
    REJECTED,
    FAILED,
}

data class AdminAuditEvent(
    val eventId: UUID,
    val occurredAt: Instant,
    val operatorPrincipalId: UUID,
    val operatorPlatformRoles: Set<PlatformRole>,
    val action: AdminAuditAction,
    val targetType: String,
    val targetId: String,
    val result: AdminAuditResult,
    val reason: String? = null,
    val correlationId: String? = null,
    val requestId: String? = null,
    val sourceIpHash: String? = null,
    val userAgentSummary: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)
