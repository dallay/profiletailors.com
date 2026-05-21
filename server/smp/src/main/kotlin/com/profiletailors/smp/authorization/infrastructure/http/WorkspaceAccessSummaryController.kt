package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.smp.authorization.application.current.workspace.GetCurrentWorkspaceAccessSummaryQuery
import com.profiletailors.smp.authorization.application.current.workspace.WorkspaceAccessSummary
import com.profiletailors.common.domain.bus.Mediator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/authorization/workspace-access")
class WorkspaceAccessSummaryController(
    private val mediator: Mediator,
) {

    @GetMapping("/current")
    suspend fun getCurrentWorkspaceAccessSummary(): WorkspaceAccessSummary =
        mediator.send(GetCurrentWorkspaceAccessSummaryQuery)
}
