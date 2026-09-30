package com.profiletailors.smp.publishing.infrastructure.credentials

import com.profiletailors.smp.publishing.application.CredentialRetentionJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CredentialRetentionScheduler(
    private val retentionJob: CredentialRetentionJob,
    private val retentionProperties: CredentialRetentionProperties,
) {
    @Scheduled(
        fixedDelayString = "\${publishing.credentials.retention.interval:PT6H}",
        initialDelayString = "\${publishing.credentials.retention.initial-delay:PT5M}",
    )
    suspend fun runRetentionJob() {
        if (!retentionProperties.enabled) {
            return
        }
        retentionJob.run(
            dryRun = retentionProperties.dryRun,
            batchSize = retentionProperties.batchSize,
        )
    }
}
