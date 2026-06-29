package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.smp.identity.infrastructure.email.EmailProperties
import com.profiletailors.spring.boot.bus.event.EventConfiguration
import com.profiletailors.spring.boot.bus.event.EventEmitter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Configuration for identity domain events and email integration beans.
 */
@Configuration
@Import(EventConfiguration::class)
class IdentityEventConfiguration {

    @Bean
    fun domainEventEmitter(): EventEmitter<DomainEvent> = EventEmitter()

    @Bean
    fun emailProperties(
        @Value("\${app.email.sender:noreply@profiletailors.com}") sender: String,
        @Value("\${app.email.verification-subject-prefix:[Profile Tailors]}") verificationSubjectPrefix: String,
    ): EmailProperties = EmailProperties(
        sender = sender,
        verificationSubjectPrefix = verificationSubjectPrefix,
    )
}
