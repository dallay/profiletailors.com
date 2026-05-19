package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusCommand
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipStatusResult
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tenancy/workspace-memberships")
class WorkspaceMembershipController(
    private val mediator: Mediator,
) {
    @PatchMapping("/{principalId}/status")
    suspend fun updateMembershipStatus(
        @PathVariable principalId: String,
        @RequestBody request: WorkspaceMembershipStatusRequest,
    ): WorkspaceMembershipStatusResult = mediator.dispatch(
        UpdateWorkspaceMembershipStatusCommand(
            targetPrincipalId = principalId,
            targetStatus = WorkspaceMembershipStatus.valueOf(request.status),
        ),
    )
}

data class WorkspaceMembershipStatusRequest(
    val status: String,
)
