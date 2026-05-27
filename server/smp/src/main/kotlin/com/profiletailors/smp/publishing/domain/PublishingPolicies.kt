package com.profiletailors.smp.publishing.domain

import java.time.Duration
import java.time.Instant

open class PublicationStateTransitionException(
    message: String,
) : IllegalStateException(message)

class PublicationEditNotAllowedException(
    publicationId: String,
) : PublicationStateTransitionException(
    "Publication '$publicationId' can only be edited while draft, queued, or scheduled."
)

class PublicationCancellationNotAllowedException(
    publicationId: String,
) : PublicationStateTransitionException(
    "Publication '$publicationId' can only be cancelled before processing begins."
)

class PublicationRetryNotAllowedException(
    publicationId: String,
) : PublicationStateTransitionException("Publication '$publicationId' can only be retried after failure.")

class PublicationAlreadyTerminalException(
    publicationId: String,
    status: PublicationStatus,
) : PublicationStateTransitionException("Publication '$publicationId' is already terminal in status $status.")

class PublicationValidationException(
    message: String,
) : IllegalArgumentException(message)

// Policy object for publication lifecycle state transitions
@Suppress("TooManyFunctions")
object PublicationLifecyclePolicy {
    fun validateForCreation(draft: PublicationDraft) {
        require(draft.workspaceId.isNotBlank()) { "Publication workspace is required." }
        require(draft.authorPrincipalId.isNotBlank()) { "Publication author is required." }
        require(draft.socialAccountId.isNotBlank()) { "Publication account is required." }
        require(draft.status == PublicationStatus.DRAFT) { "New publications must start in DRAFT state." }
        require(draft.bodyText?.isNotBlank() == true || draft.assetIds.isNotEmpty()) {
            "Publication content requires body text or at least one asset."
        }
        validateSchedule(draft.scheduleMode, draft.scheduledFor, draft.nextSlotAfter)
    }

    fun requireEditable(publication: PublicationDraft) {
        val editableStatuses = setOf(
            PublicationStatus.DRAFT,
            PublicationStatus.QUEUED,
            PublicationStatus.SCHEDULED
        )
        if (publication.status !in editableStatuses) {
            throw PublicationEditNotAllowedException(publication.id)
        }
    }

    fun requireCancellable(publication: PublicationDraft) {
        val cancellableStatuses = setOf(
            PublicationStatus.DRAFT,
            PublicationStatus.QUEUED,
            PublicationStatus.SCHEDULED
        )
        if (publication.status !in cancellableStatuses) {
            throw PublicationCancellationNotAllowedException(publication.id)
        }
    }

    fun requireRetryable(publication: PublicationDraft) {
        if (publication.status != PublicationStatus.FAILED) {
            throw PublicationRetryNotAllowedException(publication.id)
        }
    }

    fun queue(publication: PublicationDraft, resolvedDueAt: Instant): PublicationDraft {
        if (publication.status in terminalStatuses()) {
            throw PublicationAlreadyTerminalException(publication.id, publication.status)
        }
        return publication.copy(
            status = publication.queueableStatus(),
            scheduledFor = when (publication.scheduleMode) {
                ScheduleMode.NOW -> resolvedDueAt
                ScheduleMode.SCHEDULED_AT -> publication.scheduledFor ?: resolvedDueAt
                ScheduleMode.NEXT_SLOT -> resolvedDueAt
            },
            nextSlotAfter = when (publication.scheduleMode) {
                ScheduleMode.NEXT_SLOT -> publication.nextSlotAfter ?: resolvedDueAt
                else -> publication.nextSlotAfter
            },
            failedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
    }

    fun cancel(publication: PublicationDraft, cancelledAt: Instant): PublicationDraft {
        requireCancellable(publication)
        return publication.copy(
            status = PublicationStatus.CANCELLED,
            failedAt = cancelledAt,
        )
    }

    fun markProcessing(publication: PublicationDraft): PublicationDraft {
        if (publication.status !in setOf(PublicationStatus.QUEUED, PublicationStatus.SCHEDULED)) {
            throw PublicationStateTransitionException(
                "Publication '${publication.id}' can only enter PROCESSING from QUEUED or SCHEDULED.",
            )
        }
        return publication.copy(status = PublicationStatus.PROCESSING)
    }

    fun markPublished(
        publication: PublicationDraft,
        externalPublicationId: String,
        publishedAt: Instant
    ): PublicationDraft {
        if (publication.status == PublicationStatus.CANCELLED) {
            throw PublicationAlreadyTerminalException(publication.id, publication.status)
        }
        return publication.copy(
            status = PublicationStatus.PUBLISHED,
            externalPublicationId = externalPublicationId,
            publishedAt = publishedAt,
            failedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
    }

    fun markFailed(
        publication: PublicationDraft,
        failedAt: Instant,
        errorCode: String?,
        errorMessage: String?
    ): PublicationDraft =
        publication.copy(
            status = PublicationStatus.FAILED,
            failedAt = failedAt,
            lastErrorCode = errorCode,
            lastErrorMessage = errorMessage,
        )

    fun prepareRetry(publication: PublicationDraft): PublicationDraft {
        requireRetryable(publication)
        return publication.copy(
            status = publication.queueableStatus(),
            failedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
    }

    private fun validateSchedule(scheduleMode: ScheduleMode, scheduledFor: Instant?, nextSlotAfter: Instant?) {
        when (scheduleMode) {
            ScheduleMode.NOW -> require(scheduledFor == null) {
                "NOW publications must not provide scheduledFor."
            }
            ScheduleMode.SCHEDULED_AT -> requireNotNull(scheduledFor) {
                "SCHEDULED_AT publications require scheduledFor."
            }
            ScheduleMode.NEXT_SLOT -> require(nextSlotAfter != null || scheduledFor == null) {
                "NEXT_SLOT publications use nextSlotAfter instead of scheduledFor."
            }
        }
    }

    private fun terminalStatuses(): Set<PublicationStatus> = setOf(
        PublicationStatus.PUBLISHED,
        PublicationStatus.CANCELLED,
    )
}

class PublicationSchedulingPolicy {
    fun resolveDueAt(publication: PublicationDraft, now: Instant): Instant = when (publication.scheduleMode) {
        ScheduleMode.NOW -> now
        ScheduleMode.SCHEDULED_AT -> publication.scheduledFor
            ?: throw PublicationValidationException("SCHEDULED_AT publications require scheduledFor.")
        ScheduleMode.NEXT_SLOT -> publication.nextSlotAfter ?: now
    }

    fun priorityRank(publication: PublicationDraft): Int =
        if (publication.priority) PRIORITY_RANK else NORMAL_RANK

    private companion object {
        const val PRIORITY_RANK = 100
        const val NORMAL_RANK = 0
    }
}

class DeliveryRetryPolicy(
    private val maxRetries: Int,
    private val retryBackoff: Duration,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative." }
        require(!retryBackoff.isNegative) { "retryBackoff must not be negative." }
    }

    fun shouldRetry(currentAttemptNumber: Int, retryable: Boolean): Boolean =
        retryable && currentAttemptNumber <= maxRetries

    fun nextRetryAt(failedAt: Instant): Instant = failedAt.plus(retryBackoff)

    fun maxAttempts(): Int = maxRetries + 1
}
