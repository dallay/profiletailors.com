package com.profiletailors.smp.governance.application

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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AdminTakedownCommandAdapterTest {

    private val repository: TakedownReportRepository = mockk()
    private val mediaAssetStatus: MediaAssetStatusUpdater = mockk()
    private val auditHook: AuditHook = mockk()
    private val eventPublisher: EventPublisher<DomainEvent> = mockk()
    private val adapter = AdminTakedownCommandAdapter(
        repository = repository,
        mediaAssetStatus = mediaAssetStatus,
        auditHook = auditHook,
        eventPublisher = eventPublisher,
    )

    @Test
    fun `approve suspends asset records workspace audit and publishes approved email`() = runTest {
        coEvery { repository.findByReportId("report-001") } returns reported()
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { mediaAssetStatus.updateAssetStatus(any()) } returns Unit
        coEvery { auditHook.onMutation(any()) } returns Unit
        coEvery { eventPublisher.publish(any<DomainEvent>()) } returns Unit

        val result = adapter.approve(reportId = "report-001", operatorId = "operator-1")

        result.status shouldBe "APPROVED"
        result.reviewedById shouldBe "operator-1"
        result::class.simpleName shouldBe "AdminTakedownReport"
        coVerify {
            mediaAssetStatus.updateAssetStatus(
                match { update: AssetStatusUpdate ->
                    update.workspaceId == "ws-001" &&
                        update.assetId == "asset-001" &&
                        update.status == AssetStatus.SUSPENDED
                },
            )
            auditHook.onMutation(
                match { fact: MutationAuditFact ->
                    fact.action == "MEDIA_TAKEDOWN_APPROVED" &&
                        fact.targetType == "takedown_report" &&
                        fact.targetId == "report-001" &&
                        fact.actorPrincipalId == "operator-1" &&
                        fact.workspaceId == "ws-001" &&
                        fact.outcome == MutationAuditOutcome.SUCCESS
                },
            )
            eventPublisher.publish(
                match<TakedownApproved> { event ->
                    event.reportId == "report-001" &&
                        event.workspaceId == "ws-001" &&
                        event.assetId == "asset-001" &&
                        event.reporterEmail == "reporter@example.com" &&
                        event.reviewedById == "operator-1"
                },
            )
        }
    }

    @Test
    fun `reject leaves asset unchanged records workspace audit and publishes rejected email`() = runTest {
        coEvery { repository.findByReportId("report-001") } returns reported()
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { auditHook.onMutation(any()) } returns Unit
        coEvery { eventPublisher.publish(any<DomainEvent>()) } returns Unit

        val result = adapter.reject(
            reportId = "report-001",
            operatorId = "operator-1",
            rejectionReason = "Not infringement",
        )

        result.status shouldBe "DISMISSED"
        result.reviewedById shouldBe "operator-1"
        result.rejectionReason shouldBe "Not infringement"
        coVerify(exactly = 0) { mediaAssetStatus.updateAssetStatus(any()) }
        coVerify {
            auditHook.onMutation(
                match { fact: MutationAuditFact ->
                    fact.action == "MEDIA_TAKEDOWN_REJECTED" &&
                        fact.targetType == "takedown_report" &&
                        fact.actorPrincipalId == "operator-1" &&
                        fact.outcome == MutationAuditOutcome.SUCCESS &&
                        fact.details["rejectionReason"] == "Not infringement"
                },
            )
            eventPublisher.publish(
                match<TakedownRejected> { event ->
                    event.reportId == "report-001" &&
                        event.reviewedById == "operator-1" &&
                        event.rejectionReason == "Not infringement"
                },
            )
        }
    }

    @Test
    fun `missing report throws admin not found not workspace not found`() = runTest {
        coEvery { repository.findByReportId("missing") } returns null

        val thrown = shouldThrow<AdminTakedownReportNotFoundException> {
            adapter.approve(reportId = "missing", operatorId = "operator-1")
        }

        thrown.reportId shouldBe "missing"
        thrown::class.simpleName shouldBe "AdminTakedownReportNotFoundException"
    }

    @Test
    fun `already decided report is not reviewable and does not transition`() = runTest {
        val approved = reported().approve("other-operator", Instant.parse("2026-07-22T10:00:00Z"))
        coEvery { repository.findByReportId("report-001") } returns approved

        val thrown = shouldThrow<AdminTakedownNotReviewableException> {
            adapter.reject(
                reportId = "report-001",
                operatorId = "operator-1",
                rejectionReason = "too late",
            )
        }

        thrown.reportId shouldBe "report-001"
        thrown.status shouldBe "APPROVED"
        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { mediaAssetStatus.updateAssetStatus(any()) }
        coVerify(exactly = 0) { auditHook.onMutation(any()) }
        coVerify(exactly = 0) { eventPublisher.publish(any<DomainEvent>()) }
    }

    private fun reported() = TakedownReport(
        reportId = "report-001",
        workspaceId = "ws-001",
        assetId = "asset-001",
        reportedById = "user-001",
        reason = "Copyright infringement",
        status = TakedownReportStatus.REPORTED,
        reporterEmail = "reporter@example.com",
        createdAt = Instant.parse("2026-07-21T09:00:00Z"),
        updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
    )
}
