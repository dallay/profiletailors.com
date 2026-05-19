package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.tenancy.application.AddWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.RemoveWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.TransferWorkspaceOwnershipCommand
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipResult
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tenancy/workspace-ownership")
class WorkspaceOwnershipController(
    private val mediator: Mediator,
) {
    @PostMapping("/owners")
    suspend fun addOwner(
        @RequestBody request: WorkspaceOwnerRequest,
    ): WorkspaceOwnershipResult = mediator.dispatch(AddWorkspaceOwnerCommand(targetPrincipalId = request.principalId))

    @DeleteMapping("/owners/{principalId}")
    suspend fun removeOwner(
        @PathVariable principalId: String,
    ): WorkspaceOwnershipResult = mediator.dispatch(RemoveWorkspaceOwnerCommand(targetPrincipalId = principalId))

    @PostMapping("/owners/transfer")
    suspend fun transferOwnership(
        @RequestBody request: WorkspaceOwnerRequest,
    ): WorkspaceOwnershipResult = mediator.dispatch(
        TransferWorkspaceOwnershipCommand(targetPrincipalId = request.principalId),
    )
}

data class WorkspaceOwnerRequest(
    val principalId: String,
)
