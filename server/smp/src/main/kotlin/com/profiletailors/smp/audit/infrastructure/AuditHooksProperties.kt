package com.profiletailors.smp.audit.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for platform-level hooks.
 *
 * Controls optional cross-cutting concerns like audit logging.
 */
@ConfigurationProperties(prefix = "platform.hooks")
class AuditHooksProperties(
    val audit: Audit = Audit(),
) {
    class Audit(
        val enabled: Boolean = false,
    )
}
