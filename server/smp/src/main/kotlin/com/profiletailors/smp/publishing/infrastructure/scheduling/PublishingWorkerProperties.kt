package com.profiletailors.smp.publishing.infrastructure.scheduling

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration properties for the publishing worker.
 *
 * Controls the worker's lifecycle, polling cadence, and retry policy.
 */
@ConfigurationProperties(prefix = "publishing.worker")
class PublishingWorkerProperties(
    val enabled: Boolean = false,
    val pollInterval: Duration = Duration.parse("PT30S"),
    val blockedRecoveryInterval: Duration = Duration.parse("PT5M"),
    val claimLease: Duration = Duration.parse("PT2M"),
    val maxRetries: Int = 3,
    val retryBackoff: Duration = Duration.parse("PT5M"),
)
