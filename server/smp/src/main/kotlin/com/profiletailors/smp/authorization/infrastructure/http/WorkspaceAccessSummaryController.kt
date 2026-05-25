package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.authorization.application.current.workspace.GetCurrentWorkspaceAccessSummaryQuery
import com.profiletailors.smp.authorization.application.current.workspace.WorkspaceAccessSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ProblemDetail
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for workspace access summary retrieval.
 *
 * This controller provides access to the current user's permissions and access rights
 * within the current workspace context. Returns a summary of what actions the user
 * can perform and what resources they can access.
 *
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 *
 * ## Security Features:
 * - Requires authentication (Bearer JWT token)
 * - Requires workspace context (X-Workspace-Id header)
 * - Returns access summary specific to the authenticated user and current workspace
 *
 * @property mediator The mediator for dispatching queries.
 * @created 2026-05-24
 */
@Validated
@RestController
@RequestMapping(value = ["/api/authorization/workspace-access"])
@Tag(
    name = "Workspace Access",
    description = "Workspace access and permissions endpoints",
)
class WorkspaceAccessSummaryController(
    private val mediator: Mediator,
) {

    /**
     * Get current workspace access summary.
     *
     * Returns a summary of the authenticated user's permissions and access rights within
     * the current workspace. Includes information about roles, permissions, and accessible
     * resources.
     *
     * @return WorkspaceAccessSummary containing the user's access rights and permissions.
     */
    @Operation(
        summary = "Get current workspace access summary",
        description = "Returns a summary of the authenticated user's permissions and access rights within " +
            "the current workspace. Includes information about roles, permissions, and accessible resources. " +
            "Requires a valid JWT access token and X-Workspace-Id header.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Workspace access summary retrieved successfully",
                content = [Content(schema = Schema(implementation = WorkspaceAccessSummary::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User does not have access to the specified workspace",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Workspace not found or user is not a member",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during access summary retrieval",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @GetMapping("/current", version = "1")
    suspend fun getCurrentWorkspaceAccessSummary(): WorkspaceAccessSummary =
        mediator.send(GetCurrentWorkspaceAccessSummaryQuery)
}
