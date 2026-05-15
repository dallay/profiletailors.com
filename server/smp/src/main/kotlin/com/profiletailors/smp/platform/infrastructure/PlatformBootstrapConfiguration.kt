package com.profiletailors.smp.platform.infrastructure

import com.profiletailors.smp.platform.application.AuditHook
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.platform.application.MetricsHook
import com.profiletailors.smp.platform.application.PrincipalContextProvider
import com.profiletailors.smp.platform.application.RateLimitHook
import com.profiletailors.smp.platform.application.ResourceContextProvider
import com.profiletailors.smp.platform.application.RequestOutcome
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
    fun auditHook(): AuditHook = NoOpAuditHook()

    @Bean
    fun metricsHook(): MetricsHook = NoOpMetricsHook()

    @Bean
    fun rateLimitHook(): RateLimitHook = NoOpRateLimitHook()
}

class NoOpAuditHook : AuditHook {
    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

    override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) = Unit
}

class NoOpMetricsHook : MetricsHook {
    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit
}

class NoOpRateLimitHook : RateLimitHook {
    override suspend fun onRequestReceived(requestName: String) = Unit
}
