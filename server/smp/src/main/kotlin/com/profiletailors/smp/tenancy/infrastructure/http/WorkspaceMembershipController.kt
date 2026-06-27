package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusCommand
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipStatusResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
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
@Tag(name = "Workspace Membership", description = "Workspace membership management endpoints")
class WorkspaceMembershipController(private val mediator: Mediator) {
    @Operation(
        summary = "Update workspace membership status",
        description = "Updates a workspace member status using one of the allowed values: ACTIVE, SUSPENDED, INACTIVE.",
    )
    @PatchMapping("/{principalId}/status", consumes = ["application/json"], version = "1")
    suspend fun updateMembershipStatus(
        @Parameter(description = "Principal id", example = "user_123abc")
        @PathVariable principalId: String,
        @Valid @RequestBody request: WorkspaceMembershipStatusRequest,
    ): WorkspaceMembershipStatusResult = mediator.send(
        UpdateWorkspaceMembershipStatusCommand(
            targetPrincipalId = principalId,
            targetStatus = request.toWorkspaceMembershipStatus(),
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
) {
    fun toWorkspaceMembershipStatus(): WorkspaceMembershipStatus =
        WorkspaceMembershipStatus.entries.first { it.name == status }
}
