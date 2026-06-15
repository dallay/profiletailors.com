package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsQuery
import com.profiletailors.smp.governance.application.WorkspaceAuditEventsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Validated
@RestController
@RequestMapping(value = ["/api/governance/audit-events"])
@Tag(name = "Audit Events", description = "Workspace audit event retrieval endpoints")
class AuditEventController(
    private val mediator: Mediator,
) {
    companion object {
        private const val DEFAULT_LIMIT_VALUE = "50"
        private const val MAX_LIMIT = 100L
    }

    @Operation(summary = "List workspace audit events")
    @GetMapping(version = "1")
    suspend fun listWorkspaceAuditEvents(
        @Parameter(
            description = "Filter by target resource type",
            example = "workspace",
        )
        @RequestParam(required = false) targetType: String?,
        @Parameter(
            description = "Filter by action performed",
            example = "update",
        )
        @RequestParam(required = false) action: String?,
        @Parameter(
            description = "Filter by event type classification",
            example = "security",
        )
        @RequestParam(required = false) eventType: String?,
        @Parameter(
            description = "Filter by the principal ID of the actor who performed the action",
            example = "user_123abc",
        )
        @RequestParam(required = false) actorPrincipalId: String?,
        @Parameter(
            description = "Filter for events created after this timestamp (ISO 8601 format)",
            example = "2026-05-01T00:00:00Z",
        )
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdAfter: Instant?,
        @Parameter(
            description = "Filter for events created before this timestamp (ISO 8601 format)",
            example = "2026-05-24T23:59:59Z",
        )
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdBefore: Instant?,
        @Parameter(
            description = "Cursor for pagination (opaque token from previous response)",
            example = "eyJpZCI6MTIzNDU2fQ==",
        )
        @RequestParam(required = false) cursor: String?,
        @Parameter(
            description = "Maximum number of events to return (default: 50, max: 100)",
            example = DEFAULT_LIMIT_VALUE,
        )
        @RequestParam(required = false, defaultValue = DEFAULT_LIMIT_VALUE)
        @Min(1)
        @Max(MAX_LIMIT)
        limit: Int,
    ): WorkspaceAuditEventsResponse = mediator.send(
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
