package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.smp.governance.application.ReportTakedownCommand
import com.profiletailors.smp.governance.domain.TakedownReport
import jakarta.validation.constraints.NotBlank

/**
 * HTTP response DTO for a [TakedownReport].
 */
data class TakedownReportResponse(
    val reportId: String,
    val workspaceId: String,
    val assetId: String,
    val reportedById: String,
    val reason: String,
    val status: String,
    val rejectionReason: String?,
    val reviewedById: String?,
    val reviewedAt: String?,
    val reporterEmail: String,
    val mediaReferenceUrl: String?,
    val createdAt: String,
    val updatedAt: String,
)

internal fun TakedownReport.toResponse() = TakedownReportResponse(
    reportId = reportId,
    workspaceId = workspaceId,
    assetId = assetId,
    reportedById = reportedById,
    reason = reason,
    status = status.name,
    rejectionReason = rejectionReason,
    reviewedById = reviewedById,
    reviewedAt = reviewedAt?.toString(),
    reporterEmail = reporterEmail,
    mediaReferenceUrl = mediaReferenceUrl,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

/**
 * Request DTO for creating a new takedown report.
 *
 * The reporter email is NOT in the DTO — the backend derives it from the
 * authenticated principal's verified email address.
 */
data class ReportTakedownRequest(
    @field:NotBlank val assetId: String,
    @field:NotBlank val reason: String,
    val mediaReferenceUrl: String? = null,
) {
    fun toCommand() = ReportTakedownCommand(
        assetId = assetId,
        reason = reason,
        mediaReferenceUrl = mediaReferenceUrl,
    )
}

/**
 * Request DTO for reviewing a takedown report (approve/reject).
 */
data class ReviewTakedownRequest(@field:NotBlank val rejectionReason: String)
