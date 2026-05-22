package com.profiletailors.smp.platform.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.RequestPathProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.observability.application.MetricsHook
import com.profiletailors.smp.observability.application.RateLimitHook
import com.profiletailors.smp.platform.infrastructure.http.RequestPathWebFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.WebFilter
import java.time.Clock

@Configuration
class PlatformBootstrapConfiguration {

    @Bean
    fun requestContextStore(): RequestContextStore = InMemoryRequestContextStore()

    @Bean("storeBackedPrincipalContextProvider")
    fun storeBackedPrincipalContextProvider(requestContextStore: RequestContextStore): PrincipalContextProvider =
        StoreBackedPrincipalContextProvider(requestContextStore)

    @Bean
    fun resourceContextProvider(requestContextStore: RequestContextStore): ResourceContextProvider =
        StoreBackedResourceContextProvider(requestContextStore)

    @Bean
    fun requestPathProvider(requestContextStore: RequestContextStore): RequestPathProvider =
        StoreBackedRequestPathProvider(requestContextStore)

    @Bean
    fun requestPathWebFilter(requestContextStore: RequestContextStore): WebFilter =
        RequestPathWebFilter(requestContextStore)

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper()

    @Bean
    fun auditHook(
        databaseClientProvider: org.springframework.beans.factory.ObjectProvider<DatabaseClient>,
        objectMapper: ObjectMapper,
        clock: Clock,
        @Value("\${platform.hooks.audit.enabled:false}") auditEnabled: Boolean,
    ): AuditHook = if (auditEnabled) {
        val databaseClient = databaseClientProvider.getIfAvailable()
            ?: throw IllegalStateException("Audit is enabled but DatabaseClient is not available")
        R2dbcAuditHook(
            databaseClient = databaseClient,
            objectMapper = objectMapper,
            clock = clock,
        )
    } else {
        NoOpAuditHook()
    }

    @Bean
    fun metricsHook(): MetricsHook = NoOpMetricsHook()

    @Bean
    fun rateLimitHook(): RateLimitHook = NoOpRateLimitHook()

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

class NoOpAuditHook : AuditHook {
    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

    override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) = Unit

    override suspend fun onMutation(fact: MutationAuditFact) = Unit
}

class NoOpMetricsHook : MetricsHook {
    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit
}

class NoOpRateLimitHook : RateLimitHook {
    override suspend fun onRequestReceived(requestName: String) = Unit
}
