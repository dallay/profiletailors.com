package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusCommand
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipStatusResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.ProblemDetail
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for workspace membership management.
 *
 * This controller handles operations related to workspace membership status updates,
 * such as activating, suspending, or deactivating member accounts. All operations
 * require workspace context via the X-Workspace-Id header and appropriate permissions.
 *
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 *
 * ## Security Features:
 * - Requires authentication (Bearer JWT token)
 * - Requires workspace context (X-Workspace-Id header)
 * - Validates user permissions for membership operations
 * - Enforces membership status transition rules
 *
 * @property mediator The mediator for dispatching commands.
 * @created 2026-05-24
 */
@Validated
@RestController
@RequestMapping(value = ["/api/tenancy/workspace-memberships"])
@Tag(
    name = "Workspace Membership",
    description = "Workspace membership management endpoints",
)
class WorkspaceMembershipController(
    private val mediator: Mediator,
) {
    /**
     * Update workspace membership status.
     *
     * Updates the membership status of a workspace member. Valid status transitions depend on
     * the current status and business rules. Requires owner or admin permissions.
     *
     * Valid statuses: ACTIVE, SUSPENDED, INACTIVE
     *
     * @param principalId The ID of the principal (user) whose membership status to update.
     * @param request The request containing the new membership status.
     * @return WorkspaceMembershipStatusResult with the operation outcome.
     */
    @Operation(
        summary = "Update workspace membership status",
        description = "Updates the membership status of a workspace member. Valid status transitions depend on " +
            "the current status and business rules. Requires owner or admin permissions. " +
            "Valid statuses: ACTIVE, SUSPENDED, INACTIVE.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Membership status updated successfully",
                content = [Content(schema = Schema(implementation = WorkspaceMembershipStatusResult::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request data - Invalid status value or transition not allowed",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User does not have permission to update membership status",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Principal not found or not a workspace member",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict - Status transition not allowed from current state",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during status update",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @PatchMapping("/{principalId}/status", consumes = ["application/json"], version = "1")
    suspend fun updateMembershipStatus(
        @Parameter(
            description = "The ID of the principal (user) whose membership status to update",
            required = true,
            example = "user_123abc",
        )
        @PathVariable principalId: String,
        @Valid @RequestBody request: WorkspaceMembershipStatusRequest,
    ): WorkspaceMembershipStatusResult = mediator.send(
        UpdateWorkspaceMembershipStatusCommand(
            targetPrincipalId = principalId,
            targetStatus = WorkspaceMembershipStatus.valueOf(request.status),
        ),
    )
}

/**
 * Request body for workspace membership status update.
 *
 * Contains the new membership status to apply.
 *
 * @property status The new membership status (ACTIVE, SUSPENDED, or INACTIVE).
 */
@Schema(description = "Workspace membership status update request")
data class WorkspaceMembershipStatusRequest(
    @field:NotBlank(message = "Status is required")
    @field:Pattern(
        regexp = "^(ACTIVE|SUSPENDED|INACTIVE)$",
        message = "Status must be one of: ACTIVE, SUSPENDED, INACTIVE",
    )
    @field:Schema(
        description = "The new membership status",
        example = "ACTIVE",
        required = true,
        allowableValues = ["ACTIVE", "SUSPENDED", "INACTIVE"],
    )
    val status: String,
)
