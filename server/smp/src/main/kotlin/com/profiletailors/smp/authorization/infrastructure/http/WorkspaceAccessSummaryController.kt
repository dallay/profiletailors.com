package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.authorization.application.current.workspace.GetCurrentWorkspaceAccessSummaryQuery
import com.profiletailors.smp.authorization.application.current.workspace.WorkspaceAccessSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(value = ["/api/authorization/workspace-access"])
@Tag(name = "Workspace Access", description = "Workspace access and permissions endpoints")
class WorkspaceAccessSummaryController(
    private val mediator: Mediator,
) {
    @Operation(summary = "Get current workspace access summary")
    @GetMapping("/current", version = "1")
    suspend fun getCurrentWorkspaceAccessSummary(): WorkspaceAccessSummary =
        mediator.send(GetCurrentWorkspaceAccessSummaryQuery)
}
