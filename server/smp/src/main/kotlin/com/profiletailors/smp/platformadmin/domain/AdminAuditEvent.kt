package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.ValueObject
import java.time.Instant
import java.util.UUID

@ValueObject
enum class AdminActorKind {
    HUMAN,
    SYSTEM_BOOTSTRAP,
}

@ValueObject
enum class AdminAuditAction {
    PLATFORM_ROLE_ASSIGNED,
    PLATFORM_ROLE_REVOKED,
    WAITLIST_ENTRY_INVITED,
    WAITLIST_ENTRY_CANCELLED,
    INVITATION_CREATED,
    INVITATION_RESENT,
    INVITATION_REVOKED,
    ADMIN_USER_VIEWED,
    ADMIN_WAITLIST_ENTRY_VIEWED,
    USER_DISABLED,
    USER_ENABLED,
    USER_SESSIONS_REVOKED,
    CONFIGURATION_CHANGED,
    NOTIFICATION_RETRIED,
    TAKEDOWN_APPROVED,
    TAKEDOWN_REJECTED,
    BOOTSTRAP_INVITATION_ISSUED,
    BOOTSTRAP_INVITATION_REPLACED,
    BOOTSTRAP_OWNER_GRANTED,
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
    val operatorPrincipalId: UUID?,
    val operatorPlatformRoles: Set<PlatformRole>,
    val actorKind: AdminActorKind = AdminActorKind.HUMAN,
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
) {
    init {
        when (actorKind) {
            AdminActorKind.HUMAN -> require(operatorPrincipalId != null)
            AdminActorKind.SYSTEM_BOOTSTRAP -> require(
                operatorPrincipalId == null && operatorPlatformRoles.isEmpty() && action in SYSTEM_BOOTSTRAP_ACTIONS,
            )
        }
    }

    private companion object {
        private val SYSTEM_BOOTSTRAP_ACTIONS = setOf(
            AdminAuditAction.BOOTSTRAP_INVITATION_ISSUED,
            AdminAuditAction.BOOTSTRAP_INVITATION_REPLACED,
            AdminAuditAction.BOOTSTRAP_OWNER_GRANTED,
        )
    }
}
