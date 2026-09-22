package com.profiletailors.smp.governance.application

import java.time.Instant

interface AdminTakedownQueryPort {
    suspend fun list(
        status: String?,
        workspaceId: String?,
        page: Int,
        size: Int,
    ): AdminTakedownPage<AdminTakedownReport>

    suspend fun get(reportId: String): AdminTakedownReportDetail
}

interface AdminTakedownCommandPort {
    suspend fun approve(reportId: String, operatorId: String): AdminTakedownReport

    suspend fun reject(reportId: String, operatorId: String, rejectionReason: String): AdminTakedownReport
}

data class AdminTakedownReport(
    val reportId: String,
    val workspaceId: String,
    val assetId: String,
    val reportedById: String,
    val reason: String,
    val status: String,
    val rejectionReason: String?,
    val reviewedById: String?,
    val reviewedAt: Instant?,
    val reporterEmail: String,
    val mediaReferenceUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AdminTakedownReportDetail(
    val reportId: String,
    val workspaceId: String,
    val assetId: String,
    val reportedById: String,
    val reason: String,
    val status: String,
    val rejectionReason: String?,
    val reviewedById: String?,
    val reviewedAt: Instant?,
    val reporterEmail: String,
    val mediaReferenceUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val assetStatus: String?,
)

data class AdminTakedownPage<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
) {
    companion object {
        fun <T> from(items: List<T>, page: Int, size: Int, totalElements: Long): AdminTakedownPage<T> {
            val totalPages = if (size > 0) {
                Math.ceilDiv(totalElements, size.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            } else {
                0
            }
            return AdminTakedownPage(
                items = items,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0,
            )
        }
    }
}

class AdminTakedownReportNotFoundException(val reportId: String) :
    RuntimeException("Admin takedown report not found: $reportId")

class AdminTakedownNotReviewableException(val reportId: String, val status: String) :
    RuntimeException("Admin takedown report $reportId is not reviewable in status $status")
