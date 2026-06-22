package com.profiletailors.smp.media.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Registers [MediaProperties] as a Spring-managed configuration properties bean.
 */
@Configuration
@EnableConfigurationProperties(MediaProperties::class)
class MediaConfiguration
