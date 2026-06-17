package com.profiletailors.smp.publishing.domain

import java.time.Duration
import java.time.Instant

internal val MIN_SCHEDULE_OFFSET: Duration = Duration.ZERO

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
    fun validateForCreation(draft: PublicationDraft, now: Instant) {
        require(draft.workspaceId.isNotBlank()) { "Publication workspace is required." }
        require(draft.authorPrincipalId.isNotBlank()) { "Publication author is required." }
        require(draft.socialAccountId.isNotBlank()) { "Publication account is required." }
        require(draft.status == PublicationStatus.DRAFT) { "New publications must start in DRAFT state." }
        require(draft.bodyText?.isNotBlank() == true || draft.assetIds.isNotEmpty()) {
            "Publication content requires body text or at least one asset."
        }
        validateSchedule(draft.scheduleMode, draft.scheduledFor, draft.nextSlotAfter, now)
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

    /**
     * Marks a publication as BLOCKED due to non-publishable account status
     * (DISABLED, REQUIRES_RECONNECT). BLOCKED publications can auto-retry
     * when the account status restores to ACTIVE.
     */
    fun markBlocked(
        publication: PublicationDraft,
        blockedAt: Instant,
        reason: String?,
    ): PublicationDraft {
        val inflightStatuses = setOf(
            PublicationStatus.QUEUED,
            PublicationStatus.SCHEDULED,
            PublicationStatus.PROCESSING,
        )
        if (publication.status !in inflightStatuses) {
            throw PublicationStateTransitionException(
                "Publication '${publication.id}' can only be blocked from " +
                    "QUEUED, SCHEDULED, or PROCESSING (current: ${publication.status}).",
            )
        }
        return publication.copy(
            status = PublicationStatus.BLOCKED,
            blockedAt = blockedAt,
            blockedReason = reason,
        )
    }

    /**
     * Prepares a BLOCKED publication for retry when the account status
     * restores to ACTIVE. Uses exponential backoff: initial 1 minute,
     * max 1 hour, max 5 retries. After max retries, transitions to FAILED.
     */
    fun prepareBlockedRetry(
        publication: PublicationDraft,
        now: Instant,
        maxRetries: Int = BLOCKED_MAX_RETRIES,
    ): PublicationDraft {
        require(publication.status == PublicationStatus.BLOCKED) {
            "Only BLOCKED publications can be retried via blocked-recovery."
        }
        val currentRetry = publication.retryCount
        if (currentRetry >= maxRetries) {
            return publication.copy(
                status = PublicationStatus.FAILED,
                failedAt = now,
                lastErrorCode = "BLOCKED_MAX_RETRIES_EXCEEDED",
                lastErrorMessage = "Publication exceeded maximum retries ($maxRetries) while blocked.",
            )
        }
        val delay = blockedRetryDelay(currentRetry)
        return publication.copy(
            status = PublicationStatus.QUEUED,
            scheduledFor = now.plus(delay),
            retryCount = currentRetry + 1,
            blockedAt = null,
            blockedReason = null,
            failedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
    }

    /**
     * Exponential backoff for BLOCKED retry: 1min, 2min, 4min, 8min, 16min, 32min, capped at 1hr.
     */
    internal fun blockedRetryDelay(retryCount: Int): java.time.Duration {
        val baseMinutes = 1L shl retryCount.coerceAtMost(6) // 2^retry, cap exponent at 6
        val cappedMinutes = baseMinutes.coerceAtMost(BLOCKED_MAX_DELAY_MINUTES)
        return java.time.Duration.ofMinutes(cappedMinutes)
    }

    fun prepareRetry(publication: PublicationDraft): PublicationDraft {
        requireRetryable(publication)
        return publication.copy(
            status = publication.queueableStatus(),
            failedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
    }

    private fun validateSchedule(
        scheduleMode: ScheduleMode,
        scheduledFor: Instant?,
        nextSlotAfter: Instant?,
        now: Instant,
    ) {
        when (scheduleMode) {
            ScheduleMode.NOW -> require(scheduledFor == null) {
                "NOW publications must not provide scheduledFor."
            }
            ScheduleMode.SCHEDULED_AT -> {
                requireNotNull(scheduledFor) {
                    "SCHEDULED_AT publications require scheduledFor."
                }
                val earliestAllowed = now.plus(MIN_SCHEDULE_OFFSET)
                require(!scheduledFor.isBefore(earliestAllowed)) {
                    "Cannot schedule a publication for a date and time in the past. " +
                        "Scheduled time must be in the future. " +
                        "Earliest allowed: $earliestAllowed"
                }
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

    private const val BLOCKED_MAX_RETRIES = 5
    private const val BLOCKED_MAX_DELAY_MINUTES = 60L
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

/**
 * Stateless domain policy that detects conflicting publications scheduled
 * for the same social account within a configurable time window.
 *
 * Excludes DRAFT, FAILED, CANCELLED, and PUBLISHED statuses.
 * Publications with null [PublicationDraft.scheduledFor] are skipped.
 */
object ConflictDetectionPolicy {

    private val DEFAULT_CONFLICT_WINDOW: Duration = Duration.ofMinutes(15)

    private val conflictStatuses = setOf(
        PublicationStatus.SCHEDULED,
        PublicationStatus.QUEUED,
    )

    /**
     * Returns a map from publication ID to the list of conflicting publication IDs.
     *
     * Algorithm:
     * 1. Filters to SCHEDULED/QUEUED with non-null scheduledFor
     * 2. Groups by socialAccountId
     * 3. Sorts each group by scheduledFor
     * 4. Flags adjacent pairs where gap < conflictWindow
     */
    fun findConflicts(
        publications: List<PublicationDraft>,
        conflictWindow: Duration = DEFAULT_CONFLICT_WINDOW,
    ): Map<String, List<String>> {
        val eligible = publications.filter { it.status in conflictStatuses && it.scheduledFor != null }

        val byAccount: Map<String, List<PublicationDraft>> = eligible.groupBy { it.socialAccountId }

        val conflictMap = mutableMapOf<String, MutableSet<String>>()

        for ((_, accountPublications) in byAccount) {
            val sorted = accountPublications.sortedBy { it.scheduledFor!! }

            for (i in sorted.indices) {
                val current = sorted[i]
                for (j in i + 1 until sorted.size) {
                    val candidate = sorted[j]
                    val gap = Duration.between(current.scheduledFor!!, candidate.scheduledFor!!).abs()

                    if (gap >= conflictWindow) break
                    conflictMap.getOrPut(current.id) { mutableSetOf() }.add(candidate.id)
                    conflictMap.getOrPut(candidate.id) { mutableSetOf() }.add(current.id)
                }
            }
        }

        return conflictMap.mapValues { it.value.toList() }
    }
}
