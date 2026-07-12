package com.profiletailors.smp.authorization.domain

import com.profiletailors.common.domain.bus.event.DomainEvent
import java.time.LocalDateTime

data class AuthorizationDecisionHandledEvent(
    val requestName: String,
    val requestPath: String,
    val permission: String,
    val principalId: String,
    val workspaceId: String?,
    val decision: String,
    val reasonCode: String,
    val roleKeys: List<String> = emptyList(),
    private val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent {
    override fun eventVersion(): Int = 1
    override fun occurredOn(): LocalDateTime = occurredAt

    companion object {
        fun create(
            query: Any,
            requestPath: String,
            permission: String,
            principalId: String,
            workspaceId: String?,
            decision: AuthorizationDecisionResult,
        ) = AuthorizationDecisionHandledEvent(
            requestName = query::class.java.name,
            requestPath = requestPath,
            permission = permission,
            principalId = principalId,
            workspaceId = workspaceId,
            decision = decision.decision.name,
            reasonCode = decision.reasonCode.name,
            roleKeys = decision.roleKeys.toList(),
        )
    }
}
