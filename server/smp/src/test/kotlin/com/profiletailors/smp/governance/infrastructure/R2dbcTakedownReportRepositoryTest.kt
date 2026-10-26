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
        val report = report(reportId = REPORT_1, workspaceId = WORKSPACE_1)

        repository.save(report)

        assertEquals(report, repository.findById(WORKSPACE_1, REPORT_1))
        assertNull(repository.findById(WORKSPACE_2, REPORT_1))
    }

    @Test
    fun `updates review fields and filters reports by status`() = runTest {
        val reported = report(reportId = REPORT_REPORTED, createdAt = Instant.parse("2026-07-20T10:00:00Z"))
        val approved = report(reportId = REPORT_APPROVED, createdAt = Instant.parse("2026-07-21T10:00:00Z"))
            .approve(REVIEWER_1, Instant.parse("2026-07-22T10:00:00Z"))

        repository.save(reported)
        repository.save(approved)

        val approvedReports = repository.findByWorkspace(WORKSPACE_1, TakedownReportStatus.APPROVED).toList()

        assertEquals(1, approvedReports.size)
        with(approvedReports.first()) {
            assertEquals(TakedownReportStatus.APPROVED, status)
            assertEquals(REVIEWER_1, reviewedById)
            assertEquals(Instant.parse("2026-07-22T10:00:00Z"), reviewedAt)
            assertEquals(REPORT_APPROVED, reportId)
        }
    }

    @Test
    fun `finds an existing report only for the same reporter and workspace`() = runTest {
        val report = report(reportId = REPORT_EXISTING, reportedById = REPORTER_1)
        repository.save(report)

        assertEquals(report, repository.findExisting(WORKSPACE_1, ASSET_1, REPORTER_1))
        assertNull(repository.findExisting(WORKSPACE_1, ASSET_1, REPORTER_2))
        assertNull(repository.findExisting(WORKSPACE_2, ASSET_1, REPORTER_1))
    }

    @Test
    fun `finds a report globally by report id across workspaces`() = runTest {
        val workspaceOne = report(reportId = REPORT_GLOBAL_1, workspaceId = WORKSPACE_1)
        val workspaceTwo = report(
            reportId = REPORT_GLOBAL_2,
            workspaceId = WORKSPACE_2,
            assetId = ASSET_2,
        )
        repository.save(workspaceOne)
        repository.save(workspaceTwo)

        assertEquals(workspaceOne, repository.findByReportId(REPORT_GLOBAL_1))
        assertEquals(workspaceTwo, repository.findByReportId(REPORT_GLOBAL_2))
        assertNull(repository.findByReportId(MISSING_REPORT))
    }

    @Test
    fun `lists and counts reports across workspaces with omitted filters unrestricted`() = runTest {
        val first = report(
            reportId = REPORT_PAGE_1,
            workspaceId = WORKSPACE_1,
            createdAt = Instant.parse("2026-07-20T10:00:00Z"),
        )
        val second = report(
            reportId = REPORT_PAGE_2,
            workspaceId = WORKSPACE_1,
            assetId = ASSET_2,
            createdAt = Instant.parse("2026-07-21T10:00:00Z"),
        ).approve(REVIEWER_1, Instant.parse("2026-07-22T10:00:00Z"))
        val third = report(
            reportId = REPORT_PAGE_3,
            workspaceId = WORKSPACE_2,
            assetId = ASSET_3,
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

        val workspaceOne = repository.findAll(status = null, workspaceId = WORKSPACE_1, page = 0, size = 25)
        assertEquals(listOf(second.reportId, first.reportId), workspaceOne.map { it.reportId })
        assertEquals(2, repository.count(status = null, workspaceId = WORKSPACE_1))

        val reportedInWorkspaceOne = repository.findAll(
            status = TakedownReportStatus.REPORTED,
            workspaceId = WORKSPACE_1,
            page = 0,
            size = 25,
        )
        assertEquals(listOf(first.reportId), reportedInWorkspaceOne.map { it.reportId })
        assertEquals(1, repository.count(status = TakedownReportStatus.REPORTED, workspaceId = WORKSPACE_1))

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
        workspaceId: String = WORKSPACE_1,
        reportedById: String = REPORTER_1,
        assetId: String = ASSET_1,
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
        private const val REPORT_1 = "0a75d5dc-2058-5b25-a2ea-03227eafed5f"
        private const val WORKSPACE_1 = "d3d9800f-120a-501e-ad01-401e54e8f766"
        private const val WORKSPACE_2 = "57af0326-afba-57ec-83e4-15ec87ecc8bc"
        private const val REPORT_REPORTED = "d6ca518e-a9a3-5a05-adcd-2226c20bcde7"
        private const val REPORT_APPROVED = "1f804646-ed16-5928-9aef-f9311cfe4115"
        private const val REVIEWER_1 = "b2d189b6-6a49-535b-adc1-50ff897652d3"
        private const val REPORT_EXISTING = "69ad6330-747a-58fd-a58f-05684b3f2cd8"
        private const val REPORTER_1 = "8f4e870c-78e7-5eb8-8ab6-2f01c491f25d"
        private const val ASSET_1 = "484cbbea-47d9-508d-83e1-b80932bcd6ab"
        private const val REPORTER_2 = "7db5c08b-f3a9-56ca-a2b5-839b43008e7c"
        private const val REPORT_GLOBAL_1 = "c2b26b75-0407-5f98-bd48-b6638090c48b"
        private const val REPORT_GLOBAL_2 = "351a3fcf-26f1-5671-8f74-88b3dad4fb81"
        private const val ASSET_2 = "0df96bc7-0111-590a-9685-bfde5811a5cd"
        private const val MISSING_REPORT = "84d63447-25c2-52d6-bd83-c1ce26f7fadf"
        private const val REPORT_PAGE_1 = "6779797f-85f4-5417-9368-a3a3905a4923"
        private const val REPORT_PAGE_2 = "23cf392f-3d17-5daa-a81d-98865190c728"
        private const val REPORT_PAGE_3 = "85953b90-c128-50d6-a9b5-ed35134acb42"
        private const val ASSET_3 = "d964278d-184e-52a1-9c08-fd03ee8478b7"

        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("takedown_report_repository")
    }
}
