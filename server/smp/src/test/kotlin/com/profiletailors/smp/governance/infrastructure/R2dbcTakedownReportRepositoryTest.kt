package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcTakedownReportRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val repository by lazy { R2dbcTakedownReportRepository(databaseClient) }

    @Test
    fun `saves and finds a report within its workspace`() = runTest {
        val report = report(reportId = "report-1", workspaceId = "workspace-1")

        repository.save(report)

        assertEquals(report, repository.findById("workspace-1", "report-1"))
        assertNull(repository.findById("workspace-2", "report-1"))
    }

    @Test
    fun `updates review fields and filters reports by status`() = runTest {
        val reported = report(reportId = "report-reported", createdAt = Instant.parse("2026-07-20T10:00:00Z"))
        val approved = report(reportId = "report-approved", createdAt = Instant.parse("2026-07-21T10:00:00Z"))
            .approve("reviewer-1", Instant.parse("2026-07-22T10:00:00Z"))

        repository.save(reported)
        repository.save(approved)

        val approvedReports = repository.findByWorkspace("workspace-1", TakedownReportStatus.APPROVED).toList()

        assertEquals(1, approvedReports.size)
        with(approvedReports.first()) {
            assertEquals(TakedownReportStatus.APPROVED, status)
            assertEquals("reviewer-1", reviewedById)
            assertEquals(Instant.parse("2026-07-22T10:00:00Z"), reviewedAt)
            assertEquals("report-approved", reportId)
        }
    }

    @Test
    fun `finds an existing report only for the same reporter and workspace`() = runTest {
        val report = report(reportId = "report-existing", reportedById = "reporter-1")
        repository.save(report)

        assertEquals(report, repository.findExisting("workspace-1", "asset-1", "reporter-1"))
        assertNull(repository.findExisting("workspace-1", "asset-1", "reporter-2"))
        assertNull(repository.findExisting("workspace-2", "asset-1", "reporter-1"))
    }

    @Test
    fun `finds a report globally by report id across workspaces`() = runTest {
        val workspaceOne = report(reportId = "report-global-1", workspaceId = "workspace-1")
        val workspaceTwo = report(
            reportId = "report-global-2",
            workspaceId = "workspace-2",
            assetId = "asset-2",
        )
        repository.save(workspaceOne)
        repository.save(workspaceTwo)

        assertEquals(workspaceOne, repository.findByReportId("report-global-1"))
        assertEquals(workspaceTwo, repository.findByReportId("report-global-2"))
        assertNull(repository.findByReportId("missing-report"))
    }

    @Test
    fun `lists and counts reports across workspaces with omitted filters unrestricted`() = runTest {
        val first = report(
            reportId = "report-page-1",
            workspaceId = "workspace-1",
            createdAt = Instant.parse("2026-07-20T10:00:00Z"),
        )
        val second = report(
            reportId = "report-page-2",
            workspaceId = "workspace-1",
            assetId = "asset-2",
            createdAt = Instant.parse("2026-07-21T10:00:00Z"),
        ).approve("reviewer-1", Instant.parse("2026-07-22T10:00:00Z"))
        val third = report(
            reportId = "report-page-3",
            workspaceId = "workspace-2",
            assetId = "asset-3",
            createdAt = Instant.parse("2026-07-23T10:00:00Z"),
        )
        repository.save(first)
        repository.save(second)
        repository.save(third)

        val unfiltered = repository.findAll(status = null, workspaceId = null, page = 0, size = 25)
        assertEquals(listOf(third.reportId, second.reportId, first.reportId), unfiltered.map { it.reportId })
        assertEquals(3, repository.count(status = null, workspaceId = null))

        val reported = repository.findAll(
            status = TakedownReportStatus.REPORTED,
            workspaceId = null,
            page = 0,
            size = 25,
        )
        assertEquals(listOf(third.reportId, first.reportId), reported.map { it.reportId })
        assertEquals(2, repository.count(status = TakedownReportStatus.REPORTED, workspaceId = null))

        val workspaceOne = repository.findAll(status = null, workspaceId = "workspace-1", page = 0, size = 25)
        assertEquals(listOf(second.reportId, first.reportId), workspaceOne.map { it.reportId })
        assertEquals(2, repository.count(status = null, workspaceId = "workspace-1"))

        val reportedInWorkspaceOne = repository.findAll(
            status = TakedownReportStatus.REPORTED,
            workspaceId = "workspace-1",
            page = 0,
            size = 25,
        )
        assertEquals(listOf(first.reportId), reportedInWorkspaceOne.map { it.reportId })
        assertEquals(1, repository.count(status = TakedownReportStatus.REPORTED, workspaceId = "workspace-1"))

        val firstPage = repository.findAll(status = null, workspaceId = null, page = 0, size = 2)
        val secondPage = repository.findAll(status = null, workspaceId = null, page = 1, size = 2)
        assertEquals(listOf(third.reportId, second.reportId), firstPage.map { it.reportId })
        assertEquals(listOf(first.reportId), secondPage.map { it.reportId })
    }

    @AfterEach
    fun cleanReports() = runTest {
        databaseClient.sql("DELETE FROM takedown_reports").fetch().rowsUpdated().awaitSingle()
    }

    private fun report(
        reportId: String,
        workspaceId: String = "workspace-1",
        reportedById: String = "reporter-1",
        assetId: String = "asset-1",
        createdAt: Instant = Instant.parse("2026-07-21T10:00:00Z"),
    ): TakedownReport = TakedownReport(
        reportId = reportId,
        workspaceId = workspaceId,
        assetId = assetId,
        reportedById = reportedById,
        reason = "Copyright infringement",
        status = TakedownReportStatus.REPORTED,
        reporterEmail = "reporter@example.com",
        mediaReferenceUrl = "https://example.com/original",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("takedown_report_repository")
    }
}
