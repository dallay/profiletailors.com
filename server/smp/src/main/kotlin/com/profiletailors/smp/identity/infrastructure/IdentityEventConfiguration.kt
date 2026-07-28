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

    /**
     * Creates an event emitter for domain events.
     *
     * @return An event emitter configured for domain events.
     */
    @Bean
    fun domainEventEmitter(): EventEmitter<DomainEvent> = EventEmitter()

    /**
     * Creates the email configuration used by identity-related services.
     *
     * @param sender The email address used as the sender.
     * @param verificationSubjectPrefix The prefix applied to verification email subjects.
     * @param publicAppUrl The public application URL included in email content.
     * @return The configured email properties.
     */
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
}
