package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.AuditEventItem
import com.profiletailors.smp.governance.domain.AuditEventPage
import java.time.Instant

data class GetWorkspaceAuditEventsQuery(
    val targetType: String? = null,
    val action: String? = null,
    val eventType: String? = null,
    val actorPrincipalId: String? = null,
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null,
    val cursor: String? = null,
    val limit: Int = 50,
) : Query<WorkspaceAuditEventsResponse>

data class WorkspaceAuditEventsResponse(
    val workspaceId: String,
    val items: List<AuditEventItem>,
    val page: AuditEventPage,
)
