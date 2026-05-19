package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.authorization.application.WorkspaceAuthorizationDecider
import com.profiletailors.smp.governance.application.AuditEventReader
import com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsHandler
import com.profiletailors.smp.platform.application.ResourceContextProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GovernanceBootstrapConfiguration {
    @Bean
    fun getWorkspaceAuditEventsHandler(
        resourceContextProvider: ResourceContextProvider,
        auditEventReader: AuditEventReader,
        workspaceAuthorizationDecider: WorkspaceAuthorizationDecider,
    ): GetWorkspaceAuditEventsHandler = GetWorkspaceAuditEventsHandler(
        resourceContextProvider = resourceContextProvider,
        auditEventReader = auditEventReader,
        workspaceAuthorizationDecider = workspaceAuthorizationDecider,
    )
}
