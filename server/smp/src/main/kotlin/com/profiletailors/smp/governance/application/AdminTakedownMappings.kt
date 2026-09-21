package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.TakedownReport

internal fun TakedownReport.toAdminReport(): AdminTakedownReport = AdminTakedownReport(
    reportId = reportId,
    workspaceId = workspaceId,
    assetId = assetId,
    reportedById = reportedById,
    reason = reason,
    status = status.name,
    rejectionReason = rejectionReason,
    reviewedById = reviewedById,
    reviewedAt = reviewedAt,
    reporterEmail = reporterEmail,
    mediaReferenceUrl = mediaReferenceUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun TakedownReport.toAdminDetail(assetStatus: String?): AdminTakedownReportDetail = AdminTakedownReportDetail(
    reportId = reportId,
    workspaceId = workspaceId,
    assetId = assetId,
    reportedById = reportedById,
    reason = reason,
    status = status.name,
    rejectionReason = rejectionReason,
    reviewedById = reviewedById,
    reviewedAt = reviewedAt,
    reporterEmail = reporterEmail,
    mediaReferenceUrl = mediaReferenceUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    assetStatus = assetStatus,
)
