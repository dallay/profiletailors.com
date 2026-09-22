package com.profiletailors.smp.governance.application

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AdminTakedownPortsTest {

    private val query: AdminTakedownQueryPort = FakeAdminTakedownQueryPort()
    private val command: AdminTakedownCommandPort = FakeAdminTakedownCommandPort()

    @Test
    fun `list returns governance page without asset status`() = runTest {
        val page = query.list(status = "REPORTED", workspaceId = "ws-a", page = 0, size = 25)

        page.shouldBeInstanceOf<AdminTakedownPage<AdminTakedownReport>>()
        page.items.single().status shouldBe "REPORTED"
        page.items.single()::class.members.none { it.name == "assetStatus" } shouldBe true
        page::class.java.name.startsWith("com.profiletailors.smp.platformadmin") shouldBe false
    }

    @Test
    fun `get includes asset status on detail only`() = runTest {
        val detail = query.get("report-1")

        detail.reportId shouldBe "report-1"
        detail.status shouldBe "REPORTED"
        detail.assetStatus shouldBe "READY"
        detail.reporterEmail shouldBe "reporter@example.com"
    }

    @Test
    fun `approve and reject accept operator id and return dto status strings`() = runTest {
        val approved = command.approve(reportId = "report-1", operatorId = "operator-1")
        val rejected = command.reject(
            reportId = "report-2",
            operatorId = "operator-1",
            rejectionReason = "Not infringement",
        )

        approved.status shouldBe "APPROVED"
        approved.reviewedById shouldBe "operator-1"
        rejected.status shouldBe "DISMISSED"
        rejected.rejectionReason shouldBe "Not infringement"
        approved::class.simpleName shouldNotBe "TakedownReport"
    }

    @Test
    fun `page type computes pagination metadata`() {
        val page = AdminTakedownPage.from(
            items = listOf(sampleReport()),
            page = 0,
            size = 25,
            totalElements = 1,
        )

        page.totalPages shouldBe 1
        page.hasNext shouldBe false
        page.hasPrevious shouldBe false
        page.totalElements shouldBe 1L
    }

    private class FakeAdminTakedownQueryPort : AdminTakedownQueryPort {
        override suspend fun list(
            status: String?,
            workspaceId: String?,
            page: Int,
            size: Int,
        ): AdminTakedownPage<AdminTakedownReport> = AdminTakedownPage.from(
            items = listOf(sampleReport(status = status ?: "REPORTED", workspaceId = workspaceId ?: "ws-a")),
            page = page,
            size = size,
            totalElements = 1,
        )

        override suspend fun get(reportId: String): AdminTakedownReportDetail = AdminTakedownReportDetail(
            reportId = reportId,
            workspaceId = "ws-a",
            assetId = "asset-1",
            reportedById = "reporter-1",
            reason = "Copyright infringement",
            status = "REPORTED",
            rejectionReason = null,
            reviewedById = null,
            reviewedAt = null,
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = "https://example.com/original",
            createdAt = Instant.parse("2026-07-21T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T10:00:00Z"),
            assetStatus = "READY",
        )
    }

    private class FakeAdminTakedownCommandPort : AdminTakedownCommandPort {
        override suspend fun approve(reportId: String, operatorId: String): AdminTakedownReport = sampleReport(
            reportId = reportId,
            status = "APPROVED",
            reviewedById = operatorId,
            reviewedAt = Instant.parse("2026-07-22T10:00:00Z"),
        )

        override suspend fun reject(
            reportId: String,
            operatorId: String,
            rejectionReason: String,
        ): AdminTakedownReport = sampleReport(
            reportId = reportId,
            status = "DISMISSED",
            rejectionReason = rejectionReason,
            reviewedById = operatorId,
            reviewedAt = Instant.parse("2026-07-22T10:00:00Z"),
        )
    }

    companion object {
        private fun sampleReport(
            reportId: String = "report-1",
            workspaceId: String = "ws-a",
            status: String = "REPORTED",
            rejectionReason: String? = null,
            reviewedById: String? = null,
            reviewedAt: Instant? = null,
        ) = AdminTakedownReport(
            reportId = reportId,
            workspaceId = workspaceId,
            assetId = "asset-1",
            reportedById = "reporter-1",
            reason = "Copyright infringement",
            status = status,
            rejectionReason = rejectionReason,
            reviewedById = reviewedById,
            reviewedAt = reviewedAt,
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = "https://example.com/original",
            createdAt = Instant.parse("2026-07-21T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-21T10:00:00Z"),
        )
    }
}
