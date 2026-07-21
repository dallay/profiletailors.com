package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class TakedownReportTest {

    @Test
    fun `creates report in REPORTED status`() {
        val report = createReport()

        assertEquals(TakedownReportStatus.REPORTED, report.status)
        assertNull(report.reviewedById)
        assertNull(report.reviewedAt)
        assertNull(report.rejectionReason)
    }

    @Test
    fun `approve transitions REPORTED to APPROVED`() {
        val report = createReport()
        val approved = report.approve("reviewer-1", Instant.parse("2026-07-21T10:00:00Z"))

        assertEquals(TakedownReportStatus.APPROVED, approved.status)
        assertEquals("reviewer-1", approved.reviewedById)
        assertEquals(Instant.parse("2026-07-21T10:00:00Z"), approved.reviewedAt)
    }

    @Test
    fun `dismiss transitions REPORTED to DISMISSED with reason`() {
        val report = createReport()
        val dismissed = report.dismiss("reviewer-1", "Insufficient evidence", Instant.parse("2026-07-21T10:00:00Z"))

        assertEquals(TakedownReportStatus.DISMISSED, dismissed.status)
        assertEquals("reviewer-1", dismissed.reviewedById)
        assertEquals("Insufficient evidence", dismissed.rejectionReason)
    }

    @Test
    fun `approve on already approved report throws`() {
        val report = createReport().approve("reviewer-1")

        val error = assertThrows(IllegalStateException::class.java) {
            report.approve("reviewer-2")
        }

        val expectedApproved =
            "Cannot approve report ${report.reportId} in status ${TakedownReportStatus.APPROVED}" +
                " — only REPORTED or SUSPENDED reports can be approved"
        assertEquals(expectedApproved, error.message)
    }

    @Test
    fun `dismiss on already dismissed report throws`() {
        val report = createReport().dismiss("reviewer-1")

        val error = assertThrows(IllegalStateException::class.java) {
            report.dismiss("reviewer-2")
        }

        val expectedDismissed =
            "Cannot dismiss report ${report.reportId} in status ${TakedownReportStatus.DISMISSED}" +
                " — only REPORTED or SUSPENDED reports can be dismissed"
        assertEquals(expectedDismissed, error.message)
    }

    @Test
    fun `approve transitions SUSPENDED to APPROVED`() {
        val report = createReport().copy(status = TakedownReportStatus.SUSPENDED)
        val approved = report.approve("reviewer-1")

        assertEquals(TakedownReportStatus.APPROVED, approved.status)
    }

    @Test
    fun `dismiss transitions SUSPENDED to DISMISSED`() {
        val report = createReport().copy(status = TakedownReportStatus.SUSPENDED)
        val dismissed = report.dismiss("reviewer-1", "No infringement found")

        assertEquals(TakedownReportStatus.DISMISSED, dismissed.status)
    }

    @Test
    fun `rejects blank reportId`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            createReport().copy(reportId = "  ")
        }

        assertEquals("Takedown report ID must not be blank", error.message)
    }

    @Test
    fun `rejects blank workspaceId`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            createReport().copy(workspaceId = "")
        }

        assertEquals("Workspace ID must not be blank", error.message)
    }

    @Test
    fun `rejects blank reporterEmail`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            createReport().copy(reporterEmail = "")
        }

        assertEquals("Reporter email must not be blank", error.message)
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
