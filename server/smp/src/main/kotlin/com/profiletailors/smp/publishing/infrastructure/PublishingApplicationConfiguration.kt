package com.profiletailors.smp.publishing.infrastructure

import com.profiletailors.smp.publishing.application.PublishingMediaIntegrationSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PublishingApplicationConfiguration {
    @Bean
    fun publishingMediaIntegrationSettings(
        @Value("\${platform.media.context.integration.enabled:true}") enabled: Boolean,
    ): PublishingMediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = enabled)
}
