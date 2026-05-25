package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.tenancy.application.AddWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.RemoveWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.TransferWorkspaceOwnershipCommand
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipResult
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
import org.springframework.http.ProblemDetail
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for workspace ownership management.
 *
 * This controller handles operations related to workspace ownership including adding owners,
 * removing owners, and transferring ownership. All operations require workspace context
 * via the X-Workspace-Id header and appropriate permissions.
 *
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 *
 * ## Security Features:
 * - Requires authentication (Bearer JWT token)
 * - Requires workspace context (X-Workspace-Id header)
 * - Validates user permissions for ownership operations
 * - Enforces ownership constraints (e.g., at least one owner must exist)
 *
 * @property mediator The mediator for dispatching commands.
 * @created 2026-05-24
 */
@Validated
@RestController
@RequestMapping(value = ["/api/tenancy/workspace-ownership"])
@Tag(
    name = "Workspace Ownership",
    description = "Workspace ownership management endpoints",
)
class WorkspaceOwnershipController(
    private val mediator: Mediator,
) {
    /**
     * Add a new owner to the workspace.
     *
     * Grants ownership privileges to the specified principal (user). The principal will gain
     * full administrative access to the workspace. Requires current user to be an owner.
     *
     * @param request The request containing the principal ID to add as owner.
     * @return WorkspaceOwnershipResult with the operation outcome.
     */
    @Operation(
        summary = "Add a new workspace owner",
        description = "Grants ownership privileges to the specified principal. The principal will gain " +
            "full administrative access to the workspace. Requires the current user to be an existing owner.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Owner added successfully",
                content = [Content(schema = Schema(implementation = WorkspaceOwnershipResult::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request data - Principal ID is required",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User does not have owner permissions",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Principal not found or not a workspace member",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict - Principal is already an owner",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during owner addition",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @PostMapping("/owners", consumes = ["application/json"], version = "1")
    suspend fun addOwner(
        @Valid @RequestBody request: WorkspaceOwnerRequest,
    ): WorkspaceOwnershipResult = mediator.send(AddWorkspaceOwnerCommand(targetPrincipalId = request.principalId))

    /**
     * Remove an owner from the workspace.
     *
     * Revokes ownership privileges from the specified principal. At least one owner must remain
     * in the workspace. Requires current user to be an owner.
     *
     * @param principalId The ID of the principal to remove as owner.
     * @return WorkspaceOwnershipResult with the operation outcome.
     */
    @Operation(
        summary = "Remove a workspace owner",
        description = "Revokes ownership privileges from the specified principal. At least one owner must remain " +
            "in the workspace. Requires the current user to be an existing owner.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Owner removed successfully",
                content = [Content(schema = Schema(implementation = WorkspaceOwnershipResult::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request - Cannot remove the last owner",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User does not have owner permissions",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Principal not found or not an owner",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during owner removal",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @DeleteMapping("/owners/{principalId}", version = "1")
    suspend fun removeOwner(
        @Parameter(
            description = "The ID of the principal to remove as owner",
            required = true,
            example = "user_123abc",
        )
        @PathVariable principalId: String,
    ): WorkspaceOwnershipResult = mediator.send(RemoveWorkspaceOwnerCommand(targetPrincipalId = principalId))

    /**
     * Transfer workspace ownership to another principal.
     *
     * Transfers primary ownership of the workspace to the specified principal. The current owner
     * may retain owner privileges or be demoted based on business rules. Requires current user
     * to be the primary owner.
     *
     * @param request The request containing the principal ID to transfer ownership to.
     * @return WorkspaceOwnershipResult with the operation outcome.
     */
    @Operation(
        summary = "Transfer workspace ownership",
        description = "Transfers primary ownership of the workspace to the specified principal. " +
            "Requires the current user to be the primary owner.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Ownership transferred successfully",
                content = [Content(schema = Schema(implementation = WorkspaceOwnershipResult::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request data - Principal ID is required",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User is not the primary owner",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Principal not found or not a workspace member",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict - Cannot transfer ownership to current owner",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during ownership transfer",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @PostMapping("/owners/transfer", consumes = ["application/json"], version = "1")
    suspend fun transferOwnership(
        @Valid @RequestBody request: WorkspaceOwnerRequest,
    ): WorkspaceOwnershipResult = mediator.send(
        TransferWorkspaceOwnershipCommand(targetPrincipalId = request.principalId),
    )
}

/**
 * Request body for workspace owner operations.
 *
 * Contains the principal ID for ownership-related operations.
 *
 * @property principalId The ID of the principal (user) to perform the operation on.
 */
@Schema(description = "Workspace owner operation request")
data class WorkspaceOwnerRequest(
    @field:NotBlank(message = "Principal ID is required")
    @field:Schema(
        description = "The ID of the principal (user) to perform the operation on",
        example = "user_123abc",
        required = true,
    )
    val principalId: String,
)
