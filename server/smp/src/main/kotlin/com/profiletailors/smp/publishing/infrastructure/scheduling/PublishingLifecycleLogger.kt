package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.publishing.domain.SocialProvider
import org.slf4j.LoggerFactory

class PublishingLifecycleLogger {
    private val log = LoggerFactory.getLogger(javaClass)

    fun claimed(
        publicationId: String,
        jobId: String,
        workspaceId: String,
        attemptNumber: Int,
        provider: SocialProvider,
    ) {
        log.info(
            "event={} publicationId={} jobId={} workspaceId={} attemptNumber={} provider={}",
            "publishing_attempt_claimed",
            publicationId,
            jobId,
            workspaceId,
            attemptNumber,
            provider,
        )
    }

    fun succeeded(
        publicationId: String,
        jobId: String,
        workspaceId: String,
        attemptNumber: Int,
        provider: SocialProvider,
        durationMs: Long,
    ) {
        log.info(
            "event={} publicationId={} jobId={} workspaceId={} attemptNumber={} provider={} outcome={} durationMs={}",
            "publishing_attempt_succeeded",
            publicationId,
            jobId,
            workspaceId,
            attemptNumber,
            provider,
            "SUCCEEDED",
            durationMs.coerceAtLeast(0),
        )
    }

    fun retryScheduled(
        publicationId: String,
        jobId: String,
        workspaceId: String,
        attemptNumber: Int,
        provider: SocialProvider,
        failureCategory: PublishingFailureCategory,
        durationMs: Long,
    ) {
        log.warn(
            FAILURE_EVENT_TEMPLATE,
            "publishing_retry_scheduled",
            publicationId,
            jobId,
            workspaceId,
            attemptNumber,
            provider,
            "FAILED",
            failureCategory.code,
            true,
            durationMs.coerceAtLeast(0),
        )
    }

    fun blocked(
        publicationId: String,
        jobId: String,
        workspaceId: String,
        attemptNumber: Int,
        provider: SocialProvider,
        failureCategory: PublishingFailureCategory,
        durationMs: Long,
    ) {
        log.warn(
            FAILURE_EVENT_TEMPLATE,
            "publishing_blocked",
            publicationId,
            jobId,
            workspaceId,
            attemptNumber,
            provider,
            "BLOCKED",
            failureCategory.code,
            false,
            durationMs.coerceAtLeast(0),
        )
    }

    fun terminalFailure(
        publicationId: String,
        jobId: String,
        workspaceId: String,
        attemptNumber: Int,
        provider: SocialProvider,
        failureCategory: PublishingFailureCategory,
        durationMs: Long,
    ) {
        log.error(
            FAILURE_EVENT_TEMPLATE,
            "publishing_terminal_failure",
            publicationId,
            jobId,
            workspaceId,
            attemptNumber,
            provider,
            "FAILED",
            failureCategory.code,
            false,
            durationMs.coerceAtLeast(0),
        )
    }

    private companion object {
        const val FAILURE_EVENT_TEMPLATE =
            "event={} publicationId={} jobId={} workspaceId={} attemptNumber={} provider={} " +
                "outcome={} failureCategory={} retryable={} durationMs={}"
    }
}
