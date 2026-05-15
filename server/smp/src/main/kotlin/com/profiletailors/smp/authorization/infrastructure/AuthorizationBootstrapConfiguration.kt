package com.profiletailors.smp.authorization.infrastructure

import com.profiletailors.smp.authorization.application.DirectGrantResolver
import com.profiletailors.smp.authorization.application.EntitlementResolver
import com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryHandler
import com.profiletailors.smp.authorization.application.NoOpDirectGrantResolver
import com.profiletailors.smp.authorization.application.NoOpEntitlementResolver
import com.profiletailors.smp.authorization.application.NoOpScopeResolver
import com.profiletailors.smp.authorization.application.ScopeResolver
import com.profiletailors.smp.authorization.application.WorkspaceAuthorizationDecider
import com.profiletailors.smp.authorization.application.WorkspaceAuthorizationService
import com.profiletailors.smp.authorization.application.WorkspaceMembershipResolver
import com.profiletailors.smp.authorization.application.WorkspaceMembershipRoleResolver
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthorizationBootstrapConfiguration {

    @Bean
    fun directGrantResolver(): DirectGrantResolver = NoOpDirectGrantResolver()

    @Bean
    fun scopeResolver(): ScopeResolver = NoOpScopeResolver()

    @Bean
    fun entitlementResolver(): EntitlementResolver = NoOpEntitlementResolver()

    @Bean
    fun workspaceAuthorizationDecider(
        principalContextProvider: PrincipalContextProvider,
        resourceContextProvider: ResourceContextProvider,
        workspaceMembershipResolver: WorkspaceMembershipResolver,
        workspaceMembershipRoleResolver: WorkspaceMembershipRoleResolver,
        directGrantResolver: DirectGrantResolver,
        scopeResolver: ScopeResolver,
        entitlementResolver: EntitlementResolver,
    ): WorkspaceAuthorizationDecider = WorkspaceAuthorizationService(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        workspaceMembershipResolver = workspaceMembershipResolver,
        workspaceMembershipRoleResolver = workspaceMembershipRoleResolver,
        directGrantResolver = directGrantResolver,
        scopeResolver = scopeResolver,
        entitlementResolver = entitlementResolver,
    )

    @Bean
    fun getCurrentWorkspaceAccessSummaryHandler(
        principalContextProvider: PrincipalContextProvider,
        resourceContextProvider: ResourceContextProvider,
        workspaceMembershipResolver: WorkspaceMembershipResolver,
        workspaceMembershipRoleResolver: WorkspaceMembershipRoleResolver,
        workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
        auditHook: AuditHook,
    ): GetCurrentWorkspaceAccessSummaryHandler = GetCurrentWorkspaceAccessSummaryHandler(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        workspaceMembershipResolver = workspaceMembershipResolver,
        workspaceMembershipRoleResolver = workspaceMembershipRoleResolver,
        workspaceAuthorizationService = workspaceAuthorizationDecider,
        auditHook = auditHook,
    )
}
