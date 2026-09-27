package com.profiletailors.smp.publishing.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.infrastructure.linkedin.HmacOAuthStateSigner
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PublishingOAuthConfiguration {
    @Bean
    fun oauthStateSigner(
        @Value("\${publishing.oauth.state-signing-secret:\${publishing.linkedin.state-signing-secret}}")
        stateSigningSecret: String,
        objectMapper: ObjectMapper,
        clock: Clock,
    ): OAuthStateSigner = HmacOAuthStateSigner(stateSigningSecret, objectMapper, clock)
}
