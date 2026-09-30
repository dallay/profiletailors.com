package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.domain.CredentialRetentionRule
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CredentialRetentionProperties::class)
class CredentialRetentionConfiguration {
    @Bean
    fun credentialRetentionRule(properties: CredentialRetentionProperties): CredentialRetentionRule =
        properties.toRule()
}
