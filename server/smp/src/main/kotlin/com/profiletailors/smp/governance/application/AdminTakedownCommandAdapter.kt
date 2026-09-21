package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import com.profiletailors.smp.governance.domain.event.TakedownApproved
import com.profiletailors.smp.governance.domain.event.TakedownRejected
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
internal class AdminTakedownCommandAdapter(
    private val repository: TakedownReportRepository,
    private val mediaAssetStatus: MediaAssetStatusUpdater,
    private val auditHook: AuditHook,
    private val eventPublisher: EventPublisher<DomainEvent>,
) : AdminTakedownCommandPort {

    override suspend fun approve(reportId: String, operatorId: String): AdminTakedownReport {
        val report = requireReported(reportId)
        val saved = repository.save(report.approve(operatorId))
        mediaAssetStatus.updateAssetStatus(
            AssetStatusUpdate(
                workspaceId = saved.workspaceId,
                assetId = saved.assetId,
                status = AssetStatus.SUSPENDED,
            ),
        )
        auditHook.onMutation(approvalFact(report, saved, operatorId))
        eventPublisher.publish(approvedEvent(saved))
        return saved.toAdminReport()
    }

    override suspend fun reject(reportId: String, operatorId: String, rejectionReason: String): AdminTakedownReport {
        val report = requireReported(reportId)
        val saved = repository.save(report.dismiss(operatorId, rejectionReason))
        auditHook.onMutation(rejectionFact(report, saved, operatorId, rejectionReason))
        eventPublisher.publish(rejectedEvent(saved))
        return saved.toAdminReport()
    }

    private suspend fun requireReported(reportId: String): TakedownReport {
        val report = repository.findByReportId(reportId)
            ?: throw AdminTakedownReportNotFoundException(reportId)
        if (report.status != TakedownReportStatus.REPORTED) {
            throw AdminTakedownNotReviewableException(reportId, report.status.name)
        }
        return report
    }

    private fun approvalFact(original: TakedownReport, saved: TakedownReport, operatorId: String): MutationAuditFact =
        MutationAuditFact(
            action = APPROVED_ACTION,
            targetType = TARGET_TYPE,
            targetId = saved.reportId,
            actorPrincipalId = operatorId,
            workspaceId = saved.workspaceId,
            outcome = MutationAuditOutcome.SUCCESS,
            details = mapOf(
                ASSET_ID_DETAIL to original.assetId,
                PREVIOUS_STATUS_DETAIL to original.status.name,
            ),
        )

    private fun rejectionFact(
        original: TakedownReport,
        saved: TakedownReport,
        operatorId: String,
        rejectionReason: String,
    ): MutationAuditFact = MutationAuditFact(
        action = REJECTED_ACTION,
        targetType = TARGET_TYPE,
        targetId = saved.reportId,
        actorPrincipalId = operatorId,
        workspaceId = saved.workspaceId,
        outcome = MutationAuditOutcome.SUCCESS,
        details = mapOf(
            ASSET_ID_DETAIL to original.assetId,
            PREVIOUS_STATUS_DETAIL to original.status.name,
            REJECTION_REASON_DETAIL to rejectionReason,
        ),
    )

    private fun approvedEvent(saved: TakedownReport): TakedownApproved {
        val reviewedById = requireNotNull(saved.reviewedById) {
            "Takedown report ${saved.reportId} is missing a reviewer after approval"
        }
        return TakedownApproved(
            reportId = saved.reportId,
            workspaceId = saved.workspaceId,
            assetId = saved.assetId,
            reporterEmail = saved.reporterEmail,
            reviewedById = reviewedById,
            occurredAt = LocalDateTime.ofInstant(saved.updatedAt, ZoneOffset.UTC),
        )
    }

    private fun rejectedEvent(saved: TakedownReport): TakedownRejected {
        val reviewedById = requireNotNull(saved.reviewedById) {
            "Takedown report ${saved.reportId} is missing a reviewer after rejection"
        }
        return TakedownRejected(
            reportId = saved.reportId,
            workspaceId = saved.workspaceId,
            assetId = saved.assetId,
            reporterEmail = saved.reporterEmail,
            reviewedById = reviewedById,
            rejectionReason = saved.rejectionReason,
            occurredAt = LocalDateTime.ofInstant(saved.updatedAt, ZoneOffset.UTC),
        )
    }

    private companion object {
        const val TARGET_TYPE = "takedown_report"
        const val APPROVED_ACTION = "MEDIA_TAKEDOWN_APPROVED"
        const val REJECTED_ACTION = "MEDIA_TAKEDOWN_REJECTED"
        const val ASSET_ID_DETAIL = "assetId"
        const val PREVIOUS_STATUS_DETAIL = "previousStatus"
        const val REJECTION_REASON_DETAIL = "rejectionReason"
    }
}
