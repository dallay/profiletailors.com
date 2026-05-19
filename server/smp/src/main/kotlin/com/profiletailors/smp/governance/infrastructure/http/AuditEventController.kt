package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsQuery
import com.profiletailors.smp.governance.application.WorkspaceAuditEventsResponse
import com.profiletailors.smp.platform.application.Mediator
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/governance/audit-events")
class AuditEventController(
    private val mediator: Mediator,
) {
    @GetMapping
    suspend fun listWorkspaceAuditEvents(
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) actorPrincipalId: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdAfter: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdBefore: Instant?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
    ): WorkspaceAuditEventsResponse = mediator.dispatch(
        GetWorkspaceAuditEventsQuery(
            targetType = targetType,
            action = action,
            eventType = eventType,
            actorPrincipalId = actorPrincipalId,
            createdAfter = createdAfter,
            createdBefore = createdBefore,
            cursor = cursor,
            limit = limit,
        ),
    )
}
