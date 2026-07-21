package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import java.time.Instant
import java.util.UUID

/**
 * Handles the creation of a new copyright/DMCA takedown report.
 *
 * 1. Authorizes the caller (requires MEDIA_TAKEDOWN permission).
 * 2. Creates and persists a new [TakedownReport] in REPORTED status.
 * 3. Records a mutation audit event.
 */
@Service
internal class ReportTakedownHandler(
    private val repository: TakedownReportRepository,
    private val resourceContextProvider: ResourceContextProvider,
    private val principalContextProvider: PrincipalContextProvider,
    private val authorizationService: GovernanceAuthorizationService,
    private val auditHook: AuditHook,
) : CommandWithResultHandler<ReportTakedownCommand, TakedownReport> {

    override suspend fun handle(command: ReportTakedownCommand): TakedownReport {
        authorizationService.authorizeMediaTakedown()

        val workspaceId = requireNotNull(resourceContextProvider.require().workspaceId) {
            "Workspace ID is required to report a takedown"
        }
        val actor = principalContextProvider.require()

        val report = TakedownReport(
            reportId = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            assetId = command.assetId,
            reportedById = actor.principalId,
            reason = command.reason,
            status = TakedownReportStatus.REPORTED,
            reporterEmail = command.reporterEmail,
            mediaReferenceUrl = command.mediaReferenceUrl,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        val saved = repository.save(report)

        auditHook.onMutation(
            MutationAuditFact(
                action = "MEDIA_TAKEDOWN_REPORTED",
                targetType = "takedown_report",
                targetId = saved.reportId,
                actorPrincipalId = actor.principalId,
                workspaceId = workspaceId,
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf(
                    "assetId" to command.assetId,
                    "reason" to command.reason,
                ),
            ),
        )

        return saved
    }
}
