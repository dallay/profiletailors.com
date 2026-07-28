package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.smp.identity.infrastructure.email.EmailProperties
import com.profiletailors.spring.boot.bus.event.EventConfiguration
import com.profiletailors.spring.boot.bus.event.EventEmitter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * Configuration for identity domain events and email integration beans.
 */
@Configuration
@Import(EventConfiguration::class)
class IdentityEventConfiguration {

    @Bean
    fun domainEventEmitter(): EventEmitter<DomainEvent> = EventEmitter()

    @Bean
    fun passwordResetEmailTaskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = PASSWORD_RESET_EMAIL_CORE_POOL_SIZE
        maxPoolSize = PASSWORD_RESET_EMAIL_MAX_POOL_SIZE
        queueCapacity = PASSWORD_RESET_EMAIL_QUEUE_CAPACITY
        setThreadNamePrefix("password-reset-email-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(PASSWORD_RESET_EMAIL_SHUTDOWN_SECONDS)
        initialize()
    }

    @Bean
    fun emailProperties(
        @Value("\${app.email.sender:noreply@profiletailors.com}") sender: String,
        @Value("\${app.email.verification-subject-prefix:[Profile Tailors]}") verificationSubjectPrefix: String,
        @Value("\${app.email.public-app-url:https://app.profiletailors.com}") publicAppUrl: String,
    ): EmailProperties = EmailProperties(
        sender = sender,
        verificationSubjectPrefix = verificationSubjectPrefix,
        publicAppUrl = publicAppUrl,
    )

    private companion object {
        const val PASSWORD_RESET_EMAIL_CORE_POOL_SIZE = 1
        const val PASSWORD_RESET_EMAIL_MAX_POOL_SIZE = 4
        const val PASSWORD_RESET_EMAIL_QUEUE_CAPACITY = 100
        const val PASSWORD_RESET_EMAIL_SHUTDOWN_SECONDS = 10
    }
}
