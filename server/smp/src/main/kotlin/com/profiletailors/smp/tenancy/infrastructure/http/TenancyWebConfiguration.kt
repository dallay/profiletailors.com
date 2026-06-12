package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.tenancy.application.ActiveWorkspaceContextResolver
import com.profiletailors.smp.tenancy.application.HeaderActiveWorkspaceContextResolver
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.WebFilter

@ConfigurationProperties(prefix = "platform.workspace-context")
data class WorkspaceContextConfigurationProperties(
    val headerName: String = "X-Workspace-Id",
)

@Configuration
@EnableConfigurationProperties(WorkspaceContextConfigurationProperties::class)
class TenancyWebConfiguration {

    @Bean
    fun activeWorkspaceContextResolver(): ActiveWorkspaceContextResolver = HeaderActiveWorkspaceContextResolver()

    @Bean
    fun workspaceContextWebFilter(
        requestContextStore: RequestContextStore,
        activeWorkspaceContextResolver: ActiveWorkspaceContextResolver,
        properties: WorkspaceContextConfigurationProperties,
    ): WebFilter = WorkspaceContextWebFilter(
        requestContextStore = requestContextStore,
        resolver = activeWorkspaceContextResolver,
        properties = WorkspaceContextProperties(headerName = properties.headerName),
    )
}
