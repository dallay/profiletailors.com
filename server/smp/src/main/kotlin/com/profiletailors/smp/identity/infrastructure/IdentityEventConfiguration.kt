package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.identity.infrastructure.email.EmailProperties
import com.profiletailors.spring.boot.bus.event.EventEmitter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for identity domain events and email integration beans.
 */
@Configuration
class IdentityEventConfiguration {

    @Bean
    fun domainEventPublisher(): EventPublisher<DomainEvent> = EventEmitter()

    @Bean
    fun emailProperties(
        @Value("\${app.email.sender:noreply@profiletailors.com}") sender: String,
        @Value("\${app.email.verification-subject-prefix:[Profile Tailors]}") verificationSubjectPrefix: String,
    ): EmailProperties = EmailProperties(
        sender = sender,
        verificationSubjectPrefix = verificationSubjectPrefix,
    )
}
