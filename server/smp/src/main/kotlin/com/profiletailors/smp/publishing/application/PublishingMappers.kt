package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ConnectedSocialChannel
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.MIN_SCHEDULE_OFFSET
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.ScheduleMode
import java.time.Instant
import java.util.UUID

internal const val MEDIA_CONTEXT_PRINCIPAL_ID = "media-context"

/**
 * Validates that a SCHEDULED_AT publication is scheduled at least [MIN_SCHEDULE_OFFSET] after the given time.
 *
 * When [scheduleMode] is SCHEDULED_AT, requires [scheduledFor] to be non-null and
 * throws if it would occur before [now] + [MIN_SCHEDULE_OFFSET].
 *
 * @throws IllegalArgumentException If [scheduleMode] is SCHEDULED_AT,
 * [scheduledFor] is null, or [scheduledFor] is before [now] + [MIN_SCHEDULE_OFFSET].
 */
internal fun requireScheduledInFuture(scheduleMode: ScheduleMode, scheduledFor: Instant?, now: Instant) {
    if (scheduleMode == ScheduleMode.SCHEDULED_AT) {
        val forTime = requireNotNull(scheduledFor) {
            "SCHEDULED_AT mode requires scheduledFor."
        }
        val earliestAllowed = now.plus(MIN_SCHEDULE_OFFSET)
        require(!forTime.isBefore(earliestAllowed)) {
            "Cannot schedule a publication for $forTime. " +
                "Scheduled time must be in the future. " +
                "Earliest allowed: $earliestAllowed"
        }
    }
}

internal fun ConnectedSocialChannel.toSummary(): ConnectedSocialChannelSummary = ConnectedSocialChannelSummary(
    socialAccountId = socialAccountId,
    connectionId = connectionId,
    provider = provider,
    accountKind = accountKind,
    displayName = displayName,
    status = status,
    avatarUrl = avatarUrl,
    connectedAt = connectedAt,
    lastSyncedAt = lastSyncedAt,
)

internal fun PublicationDraft.toResult(): PublicationResult = PublicationResult(
    publicationId = id,
    workspaceId = workspaceId,
    socialAccountId = socialAccountId,
    status = status,
    scheduleMode = scheduleMode,
    priority = priority,
    title = title,
    bodyText = bodyText,
    assetIds = assetIds,
    scheduledFor = scheduledFor,
    nextSlotAfter = nextSlotAfter,
    externalPublicationId = externalPublicationId,
    publicUrl = publicUrl,
    publishedAt = publishedAt,
)

/**
 * Converts this publication draft to a calendar view result.
 *
 * @param conflictingPublicationIds IDs of publications that conflict with this draft.
 * @return A calendar view result with conflict information.
 */
internal fun PublicationDraft.toCalendarResult(
    conflictingPublicationIds: List<String>,
    previewUrl: String?,
): CalendarPublicationResult = CalendarPublicationResult(
    id = id,
    workspaceId = workspaceId,
    socialAccountId = socialAccountId,
    provider = provider,
    status = status,
    scheduleMode = scheduleMode,
    priority = priority,
    title = title,
    bodyText = bodyText,
    assetIds = assetIds,
    scheduledFor = scheduledFor,
    hasConflict = conflictingPublicationIds.isNotEmpty(),
    conflictingPublicationIds = conflictingPublicationIds,
    externalPublicationId = externalPublicationId,
    publicUrl = publicUrl,
    publishedAt = publishedAt,
    previewUrl = previewUrl,
    blockedReason = blockedReason,
    errorCode = lastErrorCode,
)

internal fun PublicationDraft.toListItem(): ListPublicationItem = ListPublicationItem(
    id = id,
    workspaceId = workspaceId,
    socialAccountId = socialAccountId,
    provider = provider,
    status = status,
    title = title,
    bodyText = bodyText,
    scheduledFor = scheduledFor,
    publishedAt = publishedAt,
    publicUrl = publicUrl,
    externalPublicationId = externalPublicationId,
    failedAt = failedAt,
    lastErrorCode = lastErrorCode,
    lastErrorMessage = null,
    blockedReason = blockedReason,
    createdAt = createdAt,
)

internal fun replacementJobFor(
    publication: PublicationDraft,
    schedulingPolicy: PublicationSchedulingPolicy,
    now: Instant,
): PublicationJob = PublicationJob(
    id = "pjob-${UUID.randomUUID()}",
    publicationId = publication.id,
    workspaceId = publication.workspaceId,
    status = JobStatus.PENDING,
    dueAt = schedulingPolicy.resolveDueAt(publication, now),
    priorityRank = schedulingPolicy.priorityRank(publication),
    attemptCount = 0,
    maxAttempts = 1,
)
