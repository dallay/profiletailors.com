package com.profiletailors.smp.platform.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.platform.application.MetricsHook
import com.profiletailors.smp.platform.application.MutationAuditFact
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.RateLimitHook
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.application.RequestOutcome
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient
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
    fun mediator(context: org.springframework.context.ApplicationContext): Mediator = SpringMediator(context)

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
