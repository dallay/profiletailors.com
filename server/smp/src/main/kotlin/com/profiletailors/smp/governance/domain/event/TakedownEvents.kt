package com.profiletailors.smp.governance.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.time.LocalDateTime

/**
 * Published when a new takedown report is submitted.
 *
 * Consumers: [com.profiletailors.smp.governance.infrastructure.email.SendTakedownReportedEmailConsumer]
 * sends notification to workspace admins.
 */
data class TakedownReported(
    val reportId: String,
    val workspaceId: String,
    val assetId: String,
    val reportedById: String,
    val reason: String,
    val reporterEmail: String,
    val mediaReferenceUrl: String?,
    val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BaseDomainEvent(occurredAt)

/**
 * Published when a takedown report is approved and the asset is suspended.
 *
 * Consumers: [com.profiletailors.smp.governance.infrastructure.email.SendTakedownApprovedEmailConsumer]
 * sends notification to the original reporter.
 */
data class TakedownApproved(
    val reportId: String,
    val workspaceId: String,
    val assetId: String,
    val reporterEmail: String,
    val reviewedById: String,
    val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BaseDomainEvent(occurredAt)

/**
 * Published when a takedown report is rejected/dismissed.
 *
 * Consumers: [com.profiletailors.smp.governance.infrastructure.email.SendTakedownRejectedEmailConsumer]
 * sends notification to the original reporter.
 */
data class TakedownRejected(
    val reportId: String,
    val workspaceId: String,
    val assetId: String,
    val reporterEmail: String,
    val reviewedById: String,
    val rejectionReason: String?,
    val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BaseDomainEvent(occurredAt)
