package com.profiletailors.smp.governance.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class TakedownReportTest {

    @Test
    fun `creates report in REPORTED status`() {
        val report = createReport()

        report.status shouldBe TakedownReportStatus.REPORTED
        report.reviewedById shouldBe null
        report.reviewedAt shouldBe null
        report.rejectionReason shouldBe null
    }

    @Test
    fun `approve transitions REPORTED to APPROVED`() {
        val report = createReport()
        val approved = report.approve("reviewer-1", Instant.parse("2026-07-21T10:00:00Z"))

        approved.status shouldBe TakedownReportStatus.APPROVED
        approved.reviewedById shouldBe "reviewer-1"
        approved.reviewedAt shouldBe Instant.parse("2026-07-21T10:00:00Z")
    }

    @Test
    fun `dismiss transitions REPORTED to DISMISSED with reason`() {
        val report = createReport()
        val dismissed = report.dismiss("reviewer-1", "Insufficient evidence", Instant.parse("2026-07-21T10:00:00Z"))

        dismissed.status shouldBe TakedownReportStatus.DISMISSED
        dismissed.reviewedById shouldBe "reviewer-1"
        dismissed.rejectionReason shouldBe "Insufficient evidence"
    }

    @Test
    fun `approve on already approved report throws`() {
        val report = createReport().approve("reviewer-1")

        val expectedApproved =
            "Cannot approve report ${report.reportId} in status ${TakedownReportStatus.APPROVED}" +
                " — only REPORTED or SUSPENDED reports can be approved"

        val error = shouldThrow<IllegalStateException> {
            report.approve("reviewer-2")
        }

        error.message shouldBe expectedApproved
    }

    @Test
    fun `dismiss on already dismissed report throws`() {
        val report = createReport().dismiss("reviewer-1")

        val expectedDismissed =
            "Cannot dismiss report ${report.reportId} in status ${TakedownReportStatus.DISMISSED}" +
                " — only REPORTED or SUSPENDED reports can be dismissed"

        val error = shouldThrow<IllegalStateException> {
            report.dismiss("reviewer-2")
        }

        error.message shouldBe expectedDismissed
    }

    @Test
    fun `approve transitions SUSPENDED to APPROVED`() {
        val report = createReport().copy(status = TakedownReportStatus.SUSPENDED)
        val approved = report.approve("reviewer-1")

        approved.status shouldBe TakedownReportStatus.APPROVED
    }

    @Test
    fun `dismiss transitions SUSPENDED to DISMISSED`() {
        val report = createReport().copy(status = TakedownReportStatus.SUSPENDED)
        val dismissed = report.dismiss("reviewer-1", "No infringement found")

        dismissed.status shouldBe TakedownReportStatus.DISMISSED
    }

    @Test
    fun `rejects blank reportId`() {
        val error = shouldThrow<IllegalArgumentException> {
            createReport().copy(reportId = "  ")
        }

        error.message shouldBe "Takedown report ID must not be blank"
    }

    @Test
    fun `rejects blank workspaceId`() {
        val error = shouldThrow<IllegalArgumentException> {
            createReport().copy(workspaceId = "")
        }

        error.message shouldBe "Workspace ID must not be blank"
    }

    @Test
    fun `rejects blank reporterEmail`() {
        val error = shouldThrow<IllegalArgumentException> {
            createReport().copy(reporterEmail = "")
        }

        error.message shouldBe "Reporter email must not be blank"
    }

    private fun createReport() = TakedownReport(
        reportId = "report-1",
        workspaceId = "ws-1",
        assetId = "asset-1",
        reportedById = "user-1",
        reason = "Copyright infringement",
        status = TakedownReportStatus.REPORTED,
        reporterEmail = "reporter@example.com",
        createdAt = Instant.parse("2026-07-21T09:00:00Z"),
        updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
    )
}
