package com.profiletailors.smp.platformadmin.application.ports

import com.profiletailors.smp.platformadmin.application.model.AdminAuditEventSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminAuditEventsQuery
import java.util.UUID

interface AdminAuditQuery {
    suspend fun list(query: ListAdminAuditEventsQuery): PagedResult<AdminAuditEventSummary>
    suspend fun findById(eventId: UUID): AdminAuditEventSummary?
}
