package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryQuery
import com.profiletailors.smp.authorization.application.WorkspaceAccessSummary
import com.profiletailors.smp.platform.application.Mediator
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
        mediator.dispatch(GetCurrentWorkspaceAccessSummaryQuery)
}
