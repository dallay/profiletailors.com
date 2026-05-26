package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.tenancy.application.AddWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.RemoveWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.TransferWorkspaceOwnershipCommand
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(value = ["/api/tenancy/workspace-ownership"])
@Tag(name = "Workspace Ownership", description = "Workspace ownership management endpoints")
class WorkspaceOwnershipController(
    private val mediator: Mediator,
) {
    @Operation(summary = "Add a new workspace owner")
    @PostMapping("/owners", consumes = ["application/json"], version = "1")
    suspend fun addOwner(
        @Valid @RequestBody request: WorkspaceOwnerRequest,
    ): WorkspaceOwnershipResult = mediator.send(AddWorkspaceOwnerCommand(targetPrincipalId = request.principalId))

    @Operation(summary = "Remove a workspace owner")
    @DeleteMapping("/owners/{principalId}", version = "1")
    suspend fun removeOwner(
        @Parameter(
            description = "The ID of the principal to remove as owner",
            required = true,
            example = "user_123abc",
        )
        @PathVariable principalId: String,
    ): WorkspaceOwnershipResult = mediator.send(RemoveWorkspaceOwnerCommand(targetPrincipalId = principalId))

    @Operation(summary = "Transfer workspace ownership")
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
