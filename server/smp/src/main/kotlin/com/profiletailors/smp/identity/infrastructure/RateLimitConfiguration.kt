package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.CloseAccountOrchestration
import com.profiletailors.smp.identity.application.RateLimit
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateLimitConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun rateLimit(): RateLimit = InMemoryRateLimit()

    /**
     * Stub [CloseAccountOrchestration] used until [com.profiletailors.smp.privacy.application.CloseAccountOrchestrator]
     * can be registered as a Spring bean. The real orchestrator depends on 5 sub-ports
     * that are not yet implemented in the infrastructure layer.
     */
    @Bean
    fun closeAccountOrchestration(): CloseAccountOrchestration = CloseAccountOrchestration { principalId ->
        logger.warn(
            "CloseAccountOrchestration.stub: orchestration not yet implemented for principal {}",
            principalId,
        )
    }
}
