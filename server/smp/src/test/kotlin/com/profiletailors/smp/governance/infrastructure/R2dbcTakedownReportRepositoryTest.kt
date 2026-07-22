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

        assertEquals(listOf(approved), approvedReports)
    }

    @Test
    fun `finds an existing report only for the same reporter and workspace`() = runTest {
        val report = report(reportId = "report-existing", reportedById = "reporter-1")
        repository.save(report)

        assertEquals(report, repository.findExisting("workspace-1", "asset-1", "reporter-1"))
        assertNull(repository.findExisting("workspace-1", "asset-1", "reporter-2"))
        assertNull(repository.findExisting("workspace-2", "asset-1", "reporter-1"))
    }

    @AfterEach
    fun cleanReports() = runTest {
        databaseClient.sql("DELETE FROM takedown_reports").fetch().rowsUpdated().awaitSingle()
    }

    private fun report(
        reportId: String,
        workspaceId: String = "workspace-1",
        reportedById: String = "reporter-1",
        createdAt: Instant = Instant.parse("2026-07-21T10:00:00Z"),
    ): TakedownReport = TakedownReport(
        reportId = reportId,
        workspaceId = workspaceId,
        assetId = "asset-1",
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
