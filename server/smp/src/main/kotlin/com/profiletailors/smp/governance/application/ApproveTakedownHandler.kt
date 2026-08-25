package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.event.TakedownApproved
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Handles approval of a pending takedown report.
 *
 * 1. Authorizes the caller (requires MEDIA_TAKEDOWN permission).
 * 2. Loads the report and transitions status to APPROVED.
 * 3. Persists the updated report.
 * 4. Records a mutation audit event.
 *
 * @throws com.profiletailors.smp.governance.domain.TakedownReportNotFoundException
 *         if the report is not found in the current workspace.
 */
@Service
internal class ApproveTakedownHandler(
    private val repository: TakedownReportRepository,
    private val mediaAssetStatus: MediaAssetStatusUpdater,
    private val resourceContextProvider: ResourceContextProvider,
    private val principalContextProvider: PrincipalContextProvider,
    private val authorizationService: GovernanceAuthorizationService,
    private val auditHook: AuditHook,
    private val eventPublisher: EventPublisher<DomainEvent>,
) : CommandWithResultHandler<ApproveTakedownCommand, TakedownReport> {

    override suspend fun handle(command: ApproveTakedownCommand): TakedownReport {
        authorizationService.authorizeMediaTakedown()

        val workspaceId = requireNotNull(resourceContextProvider.require().workspaceId) {
            "Workspace ID is required to approve a takedown report"
        }
        val actor = principalContextProvider.require()

        val report = repository.findById(workspaceId, command.reportId)
            ?: throw TakedownReportNotFoundException(command.reportId)

        val approved = report.approve(actor.principalId, Instant.now())
        val saved = repository.save(approved)

        // Suspend the underlying media asset
        mediaAssetStatus.updateAssetStatus(
            AssetStatusUpdate(
                workspaceId = workspaceId,
                assetId = report.assetId,
                status = AssetStatus.SUSPENDED,
            ),
        )

        auditHook.onMutation(
            MutationAuditFact(
                action = "MEDIA_TAKEDOWN_APPROVED",
                targetType = "takedown_report",
                targetId = saved.reportId,
                actorPrincipalId = actor.principalId,
                workspaceId = workspaceId,
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf(
                    "assetId" to report.assetId,
                    "previousStatus" to report.status.name,
                ),
            ),
        )

        // Publish domain event for email notification to reporter
        val reviewedById = requireNotNull(saved.reviewedById) {
            "Takedown report ${saved.reportId} is missing a reviewer after approval"
        }

        eventPublisher.publish(
            TakedownApproved(
                reportId = saved.reportId,
                workspaceId = saved.workspaceId,
                assetId = saved.assetId,
                reporterEmail = saved.reporterEmail,
                reviewedById = reviewedById,
                occurredAt = LocalDateTime.ofInstant(saved.updatedAt, ZoneOffset.UTC),
            ),
        )

        return saved
    }
}
