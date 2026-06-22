package com.profiletailors.smp.credentials.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Registers [RefreshSessionConfigurationProperties] as a Spring-managed configuration bean.
 */
@Configuration
@EnableConfigurationProperties(RefreshSessionConfigurationProperties::class)
class RefreshSessionInfrastructureConfiguration
