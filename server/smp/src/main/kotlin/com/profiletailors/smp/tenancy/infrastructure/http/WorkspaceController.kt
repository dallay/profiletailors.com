package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.tenancy.application.GetWorkspacesForPrincipalQuery
import com.profiletailors.smp.tenancy.application.RenameWorkspaceCommand
import com.profiletailors.smp.tenancy.application.RenameWorkspaceResult
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceIconCommand
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceIconResult
import com.profiletailors.smp.tenancy.application.WorkspaceSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(value = ["/api/tenancy/workspaces"])
@Tag(name = "Workspace", description = "Workspace management endpoints")
class WorkspaceController(
    private val mediator: Mediator,
) {
    @Operation(
        summary = "List workspaces for the authenticated user",
        description = "Returns all workspaces the authenticated principal belongs to (active memberships only).",
    )
    @GetMapping(version = "1")
    suspend fun listWorkspaces(): ResponseEntity<List<WorkspaceSummary>> =
        ResponseEntity.ok(mediator.send(GetWorkspacesForPrincipalQuery))

    @Operation(
        summary = "Rename the active workspace",
        description = "Renames the workspace identified by the X-Workspace-Id header.",
    )
    @PatchMapping("/current/name", consumes = ["application/json"], version = "1")
    suspend fun renameWorkspace(
        @Valid @RequestBody request: RenameWorkspaceRequest,
    ): RenameWorkspaceResult = mediator.send(
        RenameWorkspaceCommand(newName = request.name),
    )

    @Operation(
        summary = "Update the active workspace icon",
        description = "Updates the icon for the workspace identified by the X-Workspace-Id header.",
    )
    @PatchMapping("/current/icon", consumes = ["application/json"], version = "1")
    suspend fun updateWorkspaceIcon(
        @Valid @RequestBody request: UpdateWorkspaceIconRequest,
    ): UpdateWorkspaceIconResult = mediator.send(
        UpdateWorkspaceIconCommand(icon = request.icon),
    )
}

@Schema(description = "Workspace icon update request")
data class UpdateWorkspaceIconRequest(
    @field:Size(max = 64)
    @field:Pattern(regexp = UpdateWorkspaceIconCommand.ICON_NAME_PATTERN, message = "Icon name must contain only lowercase letters and hyphens")
    @field:Schema(
        description = "The new workspace icon name (Lucide icon name)",
        example = "briefcase",
        nullable = true,
    )
    val icon: String?,
)

@Schema(description = "Workspace rename request")
data class RenameWorkspaceRequest(
    @field:NotBlank(message = "Workspace name is required")
    @field:Size(min = 1, max = 255, message = "Workspace name must be between 1 and 255 characters")
    @field:Schema(
        description = "The new workspace name",
        example = "My Team Workspace",
        required = true,
    )
    val name: String,
)
