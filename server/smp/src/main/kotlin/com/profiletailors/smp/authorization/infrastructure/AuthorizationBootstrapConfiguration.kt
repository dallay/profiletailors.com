package com.profiletailors.smp.authorization.infrastructure

import com.profiletailors.smp.authorization.domain.DirectGrantResolver
import com.profiletailors.smp.authorization.domain.EntitlementResolver
import com.profiletailors.smp.authorization.domain.ScopeResolver
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipRoleResolver
import com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryHandler
import com.profiletailors.smp.authorization.application.WorkspaceAuthorizationService
import com.profiletailors.smp.authorization.application.resource.getpreview.GetResourcePreviewHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import org.springframework.context.annotation.Bean
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.context.annotation.Configuration

@Configuration
class AuthorizationBootstrapConfiguration {

    @Bean
    fun directGrantResolver(
        databaseClient: DatabaseClient,
        objectMapper: ObjectMapper,
    ): DirectGrantResolver = R2dbcDirectGrantResolver(
        databaseClient = databaseClient,
        objectMapper = objectMapper,
    )

    @Bean
    fun scopeResolver(
        databaseClient: DatabaseClient,
        objectMapper: ObjectMapper,
    ): ScopeResolver = R2dbcWorkspaceTargetScopeResolver(
        databaseClient = databaseClient,
        objectMapper = objectMapper,
    )

    @Bean
    fun entitlementResolver(
        databaseClient: DatabaseClient,
    ): EntitlementResolver = R2dbcWorkspaceEntitlementResolver(databaseClient)

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

    @Bean
    fun getResourcePreviewHandler(
        principalContextProvider: PrincipalContextProvider,
        resourceContextProvider: ResourceContextProvider,
        workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
        auditHook: AuditHook,
    ): GetResourcePreviewHandler = GetResourcePreviewHandler(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        workspaceAuthorizationDecider = workspaceAuthorizationDecider,
        auditHook = auditHook,
    )
}
