package com.profiletailors.smp.governance.domain

import java.time.Instant

/**
 * A copyright/DMCA takedown report for a media asset within a workspace.
 *
 * Each report tracks the complete lifecycle from initial filing through
 * reviewer decision, with audit trail for accountability.
 */
data class TakedownReport(
    val reportId: String,
    val workspaceId: String,
    val assetId: String,
    val reportedById: String,
    val reason: String,
    val status: TakedownReportStatus,
    val rejectionReason: String? = null,
    val reviewedById: String? = null,
    val reviewedAt: Instant? = null,
    val reporterEmail: String,
    val mediaReferenceUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(reportId.isNotBlank()) { "Takedown report ID must not be blank" }
        require(workspaceId.isNotBlank()) { "Workspace ID must not be blank" }
        require(assetId.isNotBlank()) { "Asset ID must not be blank" }
        require(reportedById.isNotBlank()) { "Reporter ID must not be blank" }
        require(reason.isNotBlank()) { "Reason must not be blank" }
        require(reporterEmail.isNotBlank()) { "Reporter email must not be blank" }

        if (status == TakedownReportStatus.REPORTED) {
            require(reviewedById == null) { "Reviewed by must be null for REPORTED reports" }
            require(reviewedAt == null) { "Reviewed at must be null for REPORTED reports" }
            require(rejectionReason == null) { "Rejection reason must be null for REPORTED reports" }
        }

        if (status == TakedownReportStatus.DISMISSED) {
            require(reviewedById != null) { "Reviewed by is required for DISMISSED reports" }
            require(reviewedAt != null) { "Reviewed at is required for DISMISSED reports" }
            require(!rejectionReason.isNullOrBlank()) { "Rejection reason is required for DISMISSED reports" }
        }

        if (status == TakedownReportStatus.APPROVED) {
            require(reviewedById != null) { "Reviewed by is required for APPROVED reports" }
            require(reviewedAt != null) { "Reviewed at is required for APPROVED reports" }
        }
    }

    /**
     * Returns a copy with status set to [TakedownReportStatus.APPROVED].
     *
     * @param reviewerId The principal ID of the reviewer.
     * @param at The timestamp of the approval.
     * @throws IllegalStateException if the report is not in REPORTED or SUSPENDED status.
     */
    fun approve(reviewerId: String, at: Instant = Instant.now()): TakedownReport {
        check(status == TakedownReportStatus.REPORTED || status == TakedownReportStatus.SUSPENDED) {
            "Cannot approve report $reportId in status $status — only REPORTED or SUSPENDED reports can be approved"
        }
        return copy(
            status = TakedownReportStatus.APPROVED,
            reviewedById = reviewerId,
            reviewedAt = at,
            updatedAt = Instant.now(),
        )
    }

    /**
     * Returns a copy with status set to [TakedownReportStatus.DISMISSED].
     *
     * @param reviewerId The principal ID of the reviewer.
     * @param reason Mandatory reason for dismissal.
     * @param at The timestamp of the dismissal.
     * @throws IllegalStateException if the report is not in REPORTED or SUSPENDED status.
     */
    fun dismiss(reviewerId: String, reason: String, at: Instant = Instant.now()): TakedownReport {
        check(status == TakedownReportStatus.REPORTED || status == TakedownReportStatus.SUSPENDED) {
            "Cannot dismiss report $reportId in status $status — only REPORTED or SUSPENDED reports can be dismissed"
        }
        require(reason.isNotBlank()) { "Rejection reason must not be blank" }
        return copy(
            status = TakedownReportStatus.DISMISSED,
            reviewedById = reviewerId,
            reviewedAt = at,
            rejectionReason = reason,
            updatedAt = Instant.now(),
        )
    }
}
