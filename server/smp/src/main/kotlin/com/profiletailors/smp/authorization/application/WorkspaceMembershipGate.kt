package com.profiletailors.smp.authorization.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver

@Service
class WorkspaceMembershipGate(
    private val principalContextProvider: PrincipalContextProvider,
    private val membershipResolver: WorkspaceMembershipResolver,
) {
    suspend fun requireActiveMember(workspaceId: String) {
        val principal = principalContextProvider.require()
        val resource = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        if (membershipResolver.resolve(principal, resource)?.isActive() != true) {
            throw AuthorizationDeniedException("Active workspace membership is required.")
        }
    }
}
