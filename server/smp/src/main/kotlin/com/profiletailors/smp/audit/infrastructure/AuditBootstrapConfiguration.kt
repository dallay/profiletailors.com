package com.profiletailors.smp.audit.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.audit.domain.AuditHook
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient
import java.time.Clock

@Configuration
class AuditBootstrapConfiguration {

    @Bean
    fun auditHook(
        databaseClientProvider: org.springframework.beans.factory.ObjectProvider<DatabaseClient>,
        objectMapper: ObjectMapper,
        clock: Clock,
        @Value("\${platform.hooks.audit.enabled:false}") auditEnabled: Boolean,
    ): AuditHook = resolveAuditHook(
        auditEnabled = auditEnabled,
        databaseClient = databaseClientProvider.getIfAvailable(),
        objectMapper = objectMapper,
        clock = clock,
    )
}
