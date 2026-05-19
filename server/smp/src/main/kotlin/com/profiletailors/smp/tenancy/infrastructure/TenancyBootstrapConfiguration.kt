package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.tenancy.application.AddWorkspaceOwnerHandler
import com.profiletailors.smp.tenancy.application.RemoveWorkspaceOwnerHandler
import com.profiletailors.smp.tenancy.application.TenancyMutationAuditor
import com.profiletailors.smp.tenancy.application.TransferWorkspaceOwnershipHandler
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusHandler
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipLookup
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipRepository
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnershipPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient
import java.time.Clock

@Configuration
class TenancyBootstrapConfiguration {
    @Bean
    fun workspaceOwnershipRepository(databaseClient: DatabaseClient): WorkspaceOwnershipRepository =
        R2dbcWorkspaceOwnershipRepository(databaseClient)

    @Bean
    fun workspaceMembershipRepository(databaseClient: DatabaseClient): WorkspaceMembershipRepository =
        R2dbcWorkspaceMembershipRepository(databaseClient)

    @Bean
    fun workspaceOwnershipPolicy(): WorkspaceOwnershipPolicy = WorkspaceOwnershipPolicy()

    @Bean
    fun tenancyMutationAuditor(
        principalContextProvider: PrincipalContextProvider,
        auditHook: AuditHook,
    ): TenancyMutationAuditor = TenancyMutationAuditor(principalContextProvider, auditHook)

    @Bean
    fun addWorkspaceOwnerHandler(
        principalContextProvider: PrincipalContextProvider,
        resourceContextProvider: ResourceContextProvider,
        workspaceOwnershipRepository: WorkspaceOwnershipRepository,
        workspaceMembershipLookup: WorkspaceMembershipLookup,
        clock: Clock,
        tenancyMutationAuditor: TenancyMutationAuditor,
    ): AddWorkspaceOwnerHandler = AddWorkspaceOwnerHandler(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        workspaceOwnershipRepository = workspaceOwnershipRepository,
        workspaceMembershipLookup = workspaceMembershipLookup,
        clock = clock,
        tenancyMutationAuditor = tenancyMutationAuditor,
    )

    @Bean
    fun removeWorkspaceOwnerHandler(
        principalContextProvider: PrincipalContextProvider,
        resourceContextProvider: ResourceContextProvider,
        workspaceOwnershipRepository: WorkspaceOwnershipRepository,
        tenancyMutationAuditor: TenancyMutationAuditor,
    ): RemoveWorkspaceOwnerHandler = RemoveWorkspaceOwnerHandler(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        workspaceOwnershipRepository = workspaceOwnershipRepository,
        tenancyMutationAuditor = tenancyMutationAuditor,
    )

    @Bean
    fun transferWorkspaceOwnershipHandler(
        principalContextProvider: PrincipalContextProvider,
        resourceContextProvider: ResourceContextProvider,
        workspaceOwnershipRepository: WorkspaceOwnershipRepository,
        workspaceMembershipLookup: WorkspaceMembershipLookup,
        clock: Clock,
        tenancyMutationAuditor: TenancyMutationAuditor,
    ): TransferWorkspaceOwnershipHandler = TransferWorkspaceOwnershipHandler(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        workspaceOwnershipRepository = workspaceOwnershipRepository,
        workspaceMembershipLookup = workspaceMembershipLookup,
        clock = clock,
        tenancyMutationAuditor = tenancyMutationAuditor,
    )

    @Bean
    fun updateWorkspaceMembershipStatusHandler(
        principalContextProvider: PrincipalContextProvider,
        resourceContextProvider: ResourceContextProvider,
        workspaceOwnershipRepository: WorkspaceOwnershipRepository,
        workspaceMembershipLookup: WorkspaceMembershipLookup,
        workspaceMembershipRepository: WorkspaceMembershipRepository,
        workspaceOwnershipPolicy: WorkspaceOwnershipPolicy,
        tenancyMutationAuditor: TenancyMutationAuditor,
    ): UpdateWorkspaceMembershipStatusHandler = UpdateWorkspaceMembershipStatusHandler(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        workspaceOwnershipRepository = workspaceOwnershipRepository,
        workspaceMembershipLookup = workspaceMembershipLookup,
        workspaceMembershipRepository = workspaceMembershipRepository,
        ownershipPolicy = workspaceOwnershipPolicy,
        tenancyMutationAuditor = tenancyMutationAuditor,
    )

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
