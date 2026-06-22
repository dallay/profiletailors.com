package com.profiletailors.smp.audit.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Registers [AuditHooksProperties] as a Spring-managed configuration properties bean.
 */
@Configuration
@EnableConfigurationProperties(AuditHooksProperties::class)
class AuditHooksConfiguration
