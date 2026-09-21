package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportRepository
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AdminTakedownQueryAdapterTest {

    private val repository: TakedownReportRepository = mockk()
    private val mediaAssetStatusReader: MediaAssetStatusReader = mockk()
    private val adapter = AdminTakedownQueryAdapter(repository, mediaAssetStatusReader)

    @Test
    fun `lists reports as dtos without asset status`() = runTest {
        coEvery {
            repository.findAll(TakedownReportStatus.REPORTED, "ws-001", 0, 25)
        } returns listOf(reported())
        coEvery { repository.count(TakedownReportStatus.REPORTED, "ws-001") } returns 1

        val page = adapter.list(status = "REPORTED", workspaceId = "ws-001", page = 0, size = 25)

        page.items.single().reportId shouldBe "report-001"
        page.items.single().status shouldBe "REPORTED"
        page.items.single()::class.members.none { it.name == "assetStatus" } shouldBe true
        page.totalElements shouldBe 1
        page.items.single()::class.simpleName shouldBe "AdminTakedownReport"
    }

    @Test
    fun `omitted filters stay unrestricted`() = runTest {
        coEvery { repository.findAll(null, null, 0, 25) } returns listOf(reported())
        coEvery { repository.count(null, null) } returns 1

        val page = adapter.list(status = null, workspaceId = null, page = 0, size = 25)

        page.totalElements shouldBe 1
        page.items.single().workspaceId shouldBe "ws-001"
    }

    @Test
    fun `get includes asset status on detail`() = runTest {
        coEvery { repository.findByReportId("report-001") } returns reported()
        coEvery { mediaAssetStatusReader.readStatus("ws-001", "asset-001") } returns "READY"

        val detail = adapter.get("report-001")

        detail.reportId shouldBe "report-001"
        detail.status shouldBe "REPORTED"
        detail.assetStatus shouldBe "READY"
        detail.reporterEmail shouldBe "reporter@example.com"
    }

    @Test
    fun `get missing report throws admin not found not workspace not found`() = runTest {
        coEvery { repository.findByReportId("missing") } returns null

        val thrown = shouldThrow<AdminTakedownReportNotFoundException> {
            adapter.get("missing")
        }

        thrown.reportId shouldBe "missing"
        thrown::class.simpleName shouldBe "AdminTakedownReportNotFoundException"
    }

    private fun reported() = TakedownReport(
        reportId = "report-001",
        workspaceId = "ws-001",
        assetId = "asset-001",
        reportedById = "user-001",
        reason = "Copyright infringement",
        status = TakedownReportStatus.REPORTED,
        reporterEmail = "reporter@example.com",
        mediaReferenceUrl = "https://example.com/original",
        createdAt = Instant.parse("2026-07-21T09:00:00Z"),
        updatedAt = Instant.parse("2026-07-21T09:00:00Z"),
    )
}
