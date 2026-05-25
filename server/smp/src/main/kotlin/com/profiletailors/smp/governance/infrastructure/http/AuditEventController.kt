package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsQuery
import com.profiletailors.smp.governance.application.WorkspaceAuditEventsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ProblemDetail
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Controller for workspace audit event retrieval.
 *
 * This controller provides access to workspace audit logs, allowing administrators to track
 * and review all actions performed within a workspace. Supports filtering by various criteria
 * including target type, action, event type, actor, and time range.
 *
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 *
 * ## Security Features:
 * - Requires authentication (Bearer JWT token)
 * - Requires workspace context (X-Workspace-Id header)
 * - Validates user permissions for audit log access
 * - Returns only audit events for the current workspace
 *
 * @property mediator The mediator for dispatching queries.
 * @created 2026-05-24
 */
@Validated
@RestController
@RequestMapping(value = ["/api/governance/audit-events"])
@Tag(
    name = "Audit Events",
    description = "Workspace audit event retrieval endpoints",
)
class AuditEventController(
    private val mediator: Mediator,
) {
    /**
     * List workspace audit events with optional filtering.
     *
     * Retrieves audit events for the current workspace with support for filtering by target type,
     * action, event type, actor, and time range. Results are paginated using cursor-based pagination.
     *
     * @param targetType Optional filter by target resource type (e.g., "workspace", "user", "document").
     * @param action Optional filter by action performed (e.g., "create", "update", "delete").
     * @param eventType Optional filter by event type classification.
     * @param actorPrincipalId Optional filter by the principal ID of the actor who performed the action.
     * @param createdAfter Optional filter for events created after this timestamp (ISO 8601 format).
     * @param createdBefore Optional filter for events created before this timestamp (ISO 8601 format).
     * @param cursor Optional cursor for pagination (opaque token from previous response).
     * @param limit Maximum number of events to return (default: 50, max: 100).
     * @return WorkspaceAuditEventsResponse containing the filtered audit events and pagination metadata.
     */
    @Operation(
        summary = "List workspace audit events",
        description = "Retrieves audit events for the current workspace with support for filtering by target type, " +
            "action, event type, actor, and time range. Results are paginated using cursor-based pagination. " +
            "Requires owner or admin permissions.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Audit events retrieved successfully",
                content = [Content(schema = Schema(implementation = WorkspaceAuditEventsResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request data - Invalid filter values or date format",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User does not have permission to access audit logs",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during audit event retrieval",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
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
            example = "50",
        )
        @RequestParam(required = false, defaultValue = "50") limit: Int,
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
