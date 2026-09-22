package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.governance.application.AdminTakedownCommandPort
import com.profiletailors.smp.governance.application.AdminTakedownQueryPort
import com.profiletailors.smp.governance.application.AdminTakedownReport
import com.profiletailors.smp.governance.application.AdminTakedownReportDetail
import com.profiletailors.smp.platformadmin.application.command.ApproveAdminTakedownCommand
import com.profiletailors.smp.platformadmin.application.command.RejectAdminTakedownCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

class AdminTakedownHandlers(
    private val queryPort: AdminTakedownQueryPort,
    private val commandPort: AdminTakedownCommandPort,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    suspend fun list(
        operatorRoles: Set<PlatformRole>,
        status: String?,
        workspaceId: String?,
        page: Int,
        size: Int,
    ): PagedResult<AdminTakedownReport> {
        requireRead(operatorRoles)
        val result = queryPort.list(status, workspaceId, page, size)
        return PagedResult.of(result.items, result.page, result.size, result.totalElements)
    }

    suspend fun get(operatorRoles: Set<PlatformRole>, reportId: String): AdminTakedownReportDetail {
        requireRead(operatorRoles)
        return queryPort.get(reportId)
    }

    suspend fun approve(command: ApproveAdminTakedownCommand): AdminTakedownReport {
        requireManage(
            command.operatorPrincipalId,
            command.operatorRoles,
            AdminAuditAction.TAKEDOWN_APPROVED,
            command.reportId,
        )
        return mutate(
            command.operatorPrincipalId,
            command.operatorRoles,
            AdminAuditAction.TAKEDOWN_APPROVED,
            command.reportId,
        ) {
            commandPort.approve(command.reportId, command.operatorPrincipalId.toString())
        }
    }

    suspend fun reject(command: RejectAdminTakedownCommand): AdminTakedownReport {
        require(command.rejectionReason.isNotBlank()) { "rejectionReason must not be blank" }
        requireManage(
            command.operatorPrincipalId,
            command.operatorRoles,
            AdminAuditAction.TAKEDOWN_REJECTED,
            command.reportId,
        )
        return mutate(
            command.operatorPrincipalId,
            command.operatorRoles,
            AdminAuditAction.TAKEDOWN_REJECTED,
            command.reportId,
        ) {
            commandPort.reject(command.reportId, command.operatorPrincipalId.toString(), command.rejectionReason)
        }
    }

    private fun requireRead(operatorRoles: Set<PlatformRole>) {
        if (PlatformPermission.GOVERNANCE_READ !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.GOVERNANCE_READ)
        }
    }

    private suspend fun requireManage(
        operatorPrincipalId: UUID,
        operatorRoles: Set<PlatformRole>,
        action: AdminAuditAction,
        reportId: String,
    ) {
        if (PlatformPermission.GOVERNANCE_MANAGE !in operatorRoles.effectivePermissions()) {
            auditPublisher.publish(
                auditEvent(
                    operatorPrincipalId = operatorPrincipalId,
                    operatorRoles = operatorRoles,
                    action = action,
                    reportId = reportId,
                    result = AdminAuditResult.REJECTED,
                    reason = "Platform governance-management permission required.",
                    metadata = mapOf("reportId" to reportId),
                ),
            )
            throw PlatformAccessDeniedException(PlatformPermission.GOVERNANCE_MANAGE)
        }
    }

    private suspend fun mutate(
        operatorPrincipalId: UUID,
        operatorRoles: Set<PlatformRole>,
        action: AdminAuditAction,
        reportId: String,
        block: suspend () -> AdminTakedownReport,
    ): AdminTakedownReport = try {
        val result = block()
        auditPublisher.publish(
            auditEvent(
                operatorPrincipalId = operatorPrincipalId,
                operatorRoles = operatorRoles,
                action = action,
                reportId = result.reportId,
                result = AdminAuditResult.SUCCEEDED,
                metadata = mapOf(
                    "reportId" to result.reportId,
                    "workspaceId" to result.workspaceId,
                    "assetId" to result.assetId,
                ),
            ),
        )
        result
    } catch (error: Throwable) {
        auditPublisher.publish(
            auditEvent(
                operatorPrincipalId = operatorPrincipalId,
                operatorRoles = operatorRoles,
                action = action,
                reportId = reportId,
                result = AdminAuditResult.FAILED,
                reason = "Takedown mutation failed.",
                metadata = mapOf("reportId" to reportId),
            ),
        )
        throw error
    }

    private fun auditEvent(
        operatorPrincipalId: UUID,
        operatorRoles: Set<PlatformRole>,
        action: AdminAuditAction,
        reportId: String,
        result: AdminAuditResult,
        reason: String? = null,
        metadata: Map<String, String>,
    ) = AdminAuditEvent(
        eventId = UUID.randomUUID(),
        occurredAt = clock.instant(),
        operatorPrincipalId = operatorPrincipalId,
        operatorPlatformRoles = operatorRoles,
        action = action,
        targetType = TARGET_TYPE,
        targetId = reportId,
        result = result,
        reason = reason,
        metadata = metadata,
    )

    private companion object {
        const val TARGET_TYPE = "TAKEDOWN_REPORT"
    }
}
