package com.profiletailors.smp.publishing.infrastructure.scheduling

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Registers [PublishingWorkerProperties] as a Spring-managed configuration properties bean.
 */
@Configuration
@EnableConfigurationProperties(PublishingWorkerProperties::class)
class PublishingWorkerConfiguration
