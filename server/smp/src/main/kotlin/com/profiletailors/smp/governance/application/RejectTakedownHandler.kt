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
import java.time.Instant

/**
 * Handles rejection/dismissal of a pending takedown report.
 *
 * 1. Authorizes the caller (requires MEDIA_TAKEDOWN permission).
 * 2. Loads the report and transitions status to DISMISSED.
 * 3. Persists the updated report.
 * 4. Records a mutation audit event.
 *
 * @throws com.profiletailors.smp.governance.domain.TakedownReportNotFoundException
 *         if the report is not found in the current workspace.
 */
@Service
internal class RejectTakedownHandler(
    private val repository: TakedownReportRepository,
    private val resourceContextProvider: ResourceContextProvider,
    private val principalContextProvider: PrincipalContextProvider,
    private val authorizationService: GovernanceAuthorizationService,
    private val auditHook: AuditHook,
) : CommandWithResultHandler<RejectTakedownCommand, TakedownReport> {

    override suspend fun handle(command: RejectTakedownCommand): TakedownReport {
        authorizationService.authorizeMediaTakedown()

        val workspaceId = requireNotNull(resourceContextProvider.require().workspaceId) {
            "Workspace ID is required to reject a takedown report"
        }
        val actor = principalContextProvider.require()

        val report = repository.findById(workspaceId, command.reportId)
            ?: throw TakedownReportNotFoundException(command.reportId)

        val dismissed = report.dismiss(actor.principalId, command.reason, Instant.now())
        val saved = repository.save(dismissed)

        auditHook.onMutation(
            MutationAuditFact(
                action = "MEDIA_TAKEDOWN_REJECTED",
                targetType = "takedown_report",
                targetId = saved.reportId,
                actorPrincipalId = actor.principalId,
                workspaceId = workspaceId,
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf(
                    "assetId" to report.assetId,
                    "previousStatus" to report.status.name,
                    "rejectionReason" to (command.reason ?: ""),
                ),
            ),
        )

        return saved
    }
}
